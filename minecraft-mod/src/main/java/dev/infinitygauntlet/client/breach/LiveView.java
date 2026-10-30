package dev.infinitygauntlet.client.breach;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.network.SlicePayload;
import dev.infinitygauntlet.network.ViewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The chamber's live view of the target's world. The client only ever holds one level, so the target's surroundings
 * cannot be rendered directly from inside the chamber; instead the server streams the blocks around the target and
 * the creatures near it, the client builds those blocks into cached GPU meshes (rebuilt only when the slice
 * changes), and every other frame renders terrain and creatures from the target's own eyes into an offscreen
 * framebuffer that the chamber's screen displays.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class LiveView {
    public static final int WIDTH = 640, HEIGHT = 360;

    public record Cell(int x, int y, int z, BlockState state) {
    }

    public static final class Slice {
        public final int specimenId;
        public final BlockPos origin;
        public final int sx, sy, sz;
        public final BlockState[] states;
        public final List<Cell> exposed = new ArrayList<>();

        Slice(SlicePayload p) {
            specimenId = p.specimenId();
            origin = p.origin();
            sx = p.sizeX();
            sy = p.sizeY();
            sz = p.sizeZ();
            states = new BlockState[sx * sy * sz];
            for (int i = 0; i < states.length; i++) {
                states[i] = Block.stateById(p.states()[i]);
            }
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        BlockState s = get(x, y, z);
                        if (!s.isAir() && isExposed(x, y, z)) {
                            exposed.add(new Cell(x, y, z, s));
                        }
                    }
                }
            }
        }

        public BlockState get(int x, int y, int z) {
            return states[(y * sz + z) * sx + x];
        }

        private boolean isExposed(int x, int y, int z) {
            int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] o : d) {
                int nx = x + o[0], ny = y + o[1], nz = z + o[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                    if (o[1] != -1) {
                        return true;
                    }
                    continue;
                }
                BlockState n = get(nx, ny, nz);
                if (n.isAir() || !n.canOcclude()) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nullable
    private static Slice slice;
    private static boolean dirty;
    @Nullable
    private static ViewPayload view, previousView;
    private static int viewAge;
    private static final Map<RenderType, VertexBuffer> MESHES = new HashMap<>();
    private static final Map<Integer, Entity> DUMMIES = new HashMap<>();
    @Nullable
    private static TextureTarget target;
    @Nullable
    private static RenderType screenType;
    private static boolean hasPicture;
    private static long frame;
    private static MultiBufferSource.BufferSource entityBuffers;

    public static void slice(SlicePayload p) {
        slice = new Slice(p);
        dirty = true;
    }

    public static void view(ViewPayload p) {
        previousView = view != null && view.specimenId() == p.specimenId() ? view : p;
        view = p;
        viewAge = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (ViewPayload.Creature c : p.creatures()) {
            seen.add(c.id());
            Entity d = DUMMIES.get(c.id());
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(c.type());
            if (d == null || d.getType() != type) {
                d = type == EntityType.PLAYER
                        ? new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes(c.name().getBytes()), c.name()))
                        : type.create(mc.level);
                if (d == null) {
                    continue;
                }
                DUMMIES.put(c.id(), d);
            }
            if (d instanceof LivingEntity l) {
                l.walkAnimation.update(Math.min(1f, c.speed() * 4f), 0.4f);
                l.tickCount += 2;
            }
        }
        DUMMIES.keySet().retainAll(seen);
    }

    @Nullable
    public static Slice currentSlice(int specimenId) {
        return slice != null && slice.specimenId == specimenId ? slice : null;
    }

    @Nullable
    public static ViewPayload currentView(int specimenId) {
        return view != null && view.specimenId() == specimenId ? view : null;
    }

    /** The screen's render type, or null while there is no picture for this specimen yet. */
    @Nullable
    public static RenderType screen(int specimenId) {
        return hasPicture && view != null && view.specimenId() == specimenId ? screenType : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !RealityDistortion.inChamber() || view == null || slice == null || slice.specimenId != view.specimenId()) {
            return;
        }
        frame++;
        if (hasPicture && frame % 2 != 0) {
            return;
        }
        if (dirty) {
            rebuild(mc);
            dirty = false;
        }
        render(mc, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        hasPicture = true;
    }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        viewAge++;
    }

    // ------------------------------------------------------------------ mesh building

    private static void rebuild(Minecraft mc) {
        MESHES.values().forEach(VertexBuffer::close);
        MESHES.clear();
        BlockRenderDispatcher blocks = mc.getBlockRenderer();
        RandomSource random = RandomSource.create(42);
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        for (RenderType layer : RenderType.chunkBufferLayers()) {
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 20)) {
                BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                PoseStack pose = new PoseStack();
                for (Cell c : slice.exposed) {
                    BlockState state = c.state();
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty() && layer == RenderType.translucent()) {
                        fluidTop(bb, atlas, c, fluid);
                    }
                    if (state.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    BakedModel model = blocks.getBlockModel(state);
                    if (!model.getRenderTypes(state, random, ModelData.EMPTY).contains(layer)) {
                        continue;
                    }
                    int tint = mc.getBlockColors().getColor(state, null, null, 0);
                    float r = 1, g = 1, b = 1;
                    if (tint != -1) {
                        r = (tint >> 16 & 255) / 255f;
                        g = (tint >> 8 & 255) / 255f;
                        b = (tint & 255) / 255f;
                    }
                    pose.pushPose();
                    pose.translate(c.x(), c.y(), c.z());
                    blocks.getModelRenderer().renderModel(pose.last(), bb, state, model, r, g, b, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY, layer);
                    pose.popPose();
                }
                MeshData mesh = bb.build();
                if (mesh != null) {
                    VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vb.bind();
                    vb.upload(mesh);
                    VertexBuffer.unbind();
                    MESHES.put(layer, vb);
                }
            }
        }
    }

    private static void fluidTop(BufferBuilder bb, TextureAtlas atlas, Cell c, FluidState fluid) {
        int above = c.y() + 1;
        if (above < slice.sy && !slice.get(c.x(), above, c.z()).getFluidState().isEmpty()) {
            return;
        }
        boolean lava = fluid.is(FluidTags.LAVA);
        TextureAtlasSprite sprite = atlas.getSprite(ResourceLocation.withDefaultNamespace(lava ? "block/lava_still" : "block/water_still"));
        int color = lava ? 0xFFFFFFFF : 0xC03F76E4;
        float y = c.y() + 0.875f;
        float x0 = c.x(), z0 = c.z(), x1 = x0 + 1, z1 = z0 + 1;
        bb.addVertex(x0, y, z0, color, sprite.getU0(), sprite.getV0(), OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
        bb.addVertex(x0, y, z1, color, sprite.getU0(), sprite.getV1(), OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
        bb.addVertex(x1, y, z1, color, sprite.getU1(), sprite.getV1(), OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
        bb.addVertex(x1, y, z0, color, sprite.getU1(), sprite.getV0(), OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
    }

    // ------------------------------------------------------------------ rendering

    private static float[] sky(String dimension) {
        if (dimension.contains("nether")) {
            return new float[]{0.2f, 0.03f, 0.03f};
        }
        if (dimension.contains("end")) {
            return new float[]{0.06f, 0.05f, 0.09f};
        }
        return new float[]{0.55f, 0.7f, 1f};
    }

    private static void ensureTarget() {
        if (target != null) {
            return;
        }
        target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        TextureTarget t = target;
        screenType = RenderType.create(InfinityGauntlet.MOD_ID + "_live_view", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new RenderStateShard.EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, t.getColorTextureId()), () -> {
                        }))
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .createCompositeState(false));
        entityBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 18));
    }

    private static void render(Minecraft mc, float pt) {
        ensureTarget();
        ViewPayload now = view, before = previousView == null ? view : previousView;
        float f = Mth.clamp((viewAge + pt) / 2f, 0, 1);
        Vec3 eye = before.eye().lerp(now.eye(), f);
        float yaw = Mth.rotLerp(f, before.yaw(), now.yaw());
        float pitch = Mth.lerp(f, before.pitch(), now.pitch());
        net.minecraft.world.entity.Entity specimen = mc.level.getEntity(now.specimenId());
        String dim = specimen instanceof dev.infinitygauntlet.breach.SpecimenEntity s ? s.targetDimension() : "overworld";
        float[] sky = sky(dim);

        RenderTarget main = mc.getMainRenderTarget();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        try {
            target.bindWrite(true);
            RenderSystem.clearColor(sky[0], sky[1], sky[2], 1f);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(75), WIDTH / (float) HEIGHT, 0.05f, 160f);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.pushMatrix();
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.PI - yaw * Mth.DEG_TO_RAD, -pitch * Mth.DEG_TO_RAD, 0)
                    .conjugate(new Quaternionf());
            Matrix4f terrain = new Matrix4f().rotation(rotation).translate((float) (slice.origin.getX() - eye.x),
                    (float) (slice.origin.getY() - eye.y), (float) (slice.origin.getZ() - eye.z));
            RenderSystem.setShaderFogStart(26f);
            RenderSystem.setShaderFogEnd(40f);
            RenderSystem.setShaderFogColor(sky[0], sky[1], sky[2]);
            RenderSystem.setShaderFogShape(FogShape.CYLINDER);
            RenderSystem.enableDepthTest();
            for (RenderType layer : RenderType.chunkBufferLayers()) {
                VertexBuffer vb = MESHES.get(layer);
                if (vb == null) {
                    continue;
                }
                layer.setupRenderState();
                vb.bind();
                vb.drawWithShader(terrain, projection, RenderSystem.getShader());
                VertexBuffer.unbind();
                layer.clearRenderState();
            }
            renderCreatures(mc, eye, rotation, pt);
        } catch (RuntimeException e) {
            InfinityGauntlet.LOGGER.error("Live view render failed", e);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            main.bindWrite(true);
        }
    }

    private static void renderCreatures(Minecraft mc, Vec3 eye, Quaternionf rotation, float pt) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean shadows = mc.options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        Lighting.setupLevel();
        PoseStack pose = new PoseStack();
        pose.mulPose(rotation);
        for (ViewPayload.Creature c : view.creatures()) {
            Entity d = DUMMIES.get(c.id());
            if (d == null) {
                continue;
            }
            d.setPos(c.pos());
            d.xo = c.pos().x;
            d.yo = c.pos().y;
            d.zo = c.pos().z;
            d.setYRot(c.body());
            d.yRotO = c.body();
            d.setXRot(c.pitch());
            d.xRotO = c.pitch();
            if (d instanceof LivingEntity l) {
                l.yBodyRot = l.yBodyRotO = c.body();
                l.yHeadRot = l.yHeadRotO = c.head();
                AttributeInstance scale = l.getAttribute(Attributes.SCALE);
                if (scale != null && scale.getBaseValue() != c.scale()) {
                    scale.setBaseValue(c.scale());
                    l.refreshDimensions();
                }
                if (l instanceof AgeableMob a && a.isBaby() != c.baby()) {
                    a.setAge(c.baby() ? -24000 : 0);
                } else if (l instanceof Zombie z && z.isBaby() != c.baby()) {
                    z.setBaby(c.baby());
                }
            }
            dispatcher.render(d, c.pos().x - eye.x, c.pos().y - eye.y, c.pos().z - eye.z, c.body(), pt, pose, entityBuffers, LightTexture.FULL_BRIGHT);
        }
        entityBuffers.endBatch();
        dispatcher.setRenderShadow(shadows);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MESHES.values().forEach(VertexBuffer::close);
        MESHES.clear();
        DUMMIES.clear();
        slice = null;
        view = previousView = null;
        hasPicture = false;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

    private LiveView() {
    }
}
