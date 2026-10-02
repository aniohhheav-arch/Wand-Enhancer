package dev.riftverse.item.relic;

import dev.riftverse.item.ItemData;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.UniverseSpec;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Universe relics: every reality forges its own blades and blasters. A relic remembers the universe it came from (name,
 * designation, signature colour) and inherits that world's elemental nature and danger tier.
 */
public final class Relics {
    public enum Element {
        FIRE("Ember", 0xFF6020), FROST("Frost", 0xA0E8FF), VOID("Void", 0x9B30FF), STORM("Storm", 0x80B0FF), NATURE("Verdant", 0x5CFF9D),
        GLITCH("Glitch", 0x39FF14), STAR("Star", 0xFFF0C8), ARCANE("Arcane", 0xFF7AF0);

        public final String word;
        public final int color;

        Element(String word, int color) {
            this.word = word;
            this.color = color;
        }

        public static Element byId(int i) {
            Element[] v = values();
            return v[Math.floorMod(i, v.length)];
        }
    }

    private static final String[] BLADE_NOUNS = {"Blade", "Edge", "Fang", "Saber", "Cleaver", "Glaive"};
    private static final String[] GUN_NOUNS = {"Blaster", "Lance", "Repeater", "Caster", "Railgun", "Emitter"};

    private Relics() {}

    public static Element elementOf(Archetype a) {
        String id = a.id;
        if (id.matches(".*(cinder|magma|molten|ember|sunscar|forge|sanguine|mesa|radiance).*")) return Element.FIRE;
        if (id.matches(".*(rime|aurora|frost|glacier|crystalocean|mirror|glass).*")) return Element.FROST;
        if (id.matches(".*(hollow|void|obsidian|deepdark|echo|noir|negative|silhouette|xray|graphite|tapehorror).*")) return Element.VOID;
        if (id.matches(".*(tempest|thalassic|coral|drowned|cloud|storm).*")) return Element.STORM;
        if (id.matches(".*(xenoflora|primeval|mycelia|bloom|mire|toxic|weald|savanna|hive|petrified|neonjungle|watercolor|canvas|verdigris).*")) return Element.NATURE;
        if (id.matches(".*(corrupted|neon|glitch|corrupt|chrome|clockwork|ferrous|pixel|cyber|vhs|wireframe|blueprint|prismpixel|starforge).*")) return Element.GLITCH;
        if (id.matches(".*(astral|nebula|lunar|celestial|skyshatter|inverted|astralplane|elder|goldentemple|runic).*")) return Element.STAR;
        return Element.ARCANE;
    }

    public static int tier(UniverseSpec spec) {
        return Math.max(1, Math.min(5, 1 + Math.round(spec.hostility * 1.6f)));
    }

    /** Forges a relic of the given universe. */
    public static ItemStack forge(UniverseSpec spec, boolean gun, RandomSource r) {
        ItemStack stack = new ItemStack(gun ? RvItems.RELIC_BLASTER.get() : RvItems.RELIC_BLADE.get());
        Element el = elementOf(spec.archetype);
        String[] nouns = gun ? GUN_NOUNS : BLADE_NOUNS;
        String noun = nouns[r.nextInt(nouns.length)];
        String word = spec.archetype.epithets[r.nextInt(spec.archetype.epithets.length)];
        ItemData.edit(stack, t -> {
            CompoundTag relic = new CompoundTag();
            relic.putString("universe", spec.name);
            relic.putString("designation", spec.id.designation());
            relic.putString("name", word + " " + el.word + " " + noun);
            relic.putInt("element", el.ordinal());
            relic.putInt("color", spec.accent);
            relic.putInt("tier", tier(spec));
            relic.putInt("archetype", spec.archetype.ordinal());
            t.put("relic", relic);
        });
        return stack;
    }

    public static CompoundTag data(ItemStack stack) {
        return ItemData.read(stack).getCompound("relic");
    }

    public static int color(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.contains("color") ? t.getInt("color") : 0x8F6BFF;
    }

    public static int tier(ItemStack stack) {
        return Math.max(1, data(stack).getInt("tier"));
    }

    public static Element element(ItemStack stack) {
        return Element.byId(data(stack).getInt("element"));
    }

    public static Component name(ItemStack stack, String fallback) {
        CompoundTag t = data(stack);
        if (!t.contains("name")) return Component.literal(fallback);
        return Component.literal(t.getString("name")).withColor(color(stack));
    }

    public static String origin(ItemStack stack) {
        CompoundTag t = data(stack);
        return t.contains("universe") ? "Forged in " + t.getString("universe") + " [" + t.getString("designation") + "]" : "A relic of no known world";
    }

    /** The relic's elemental nature takes hold of whatever it strikes. */
    public static void strike(Element el, LivingEntity target, Entity attacker, int tier) {
        if (!(target.level() instanceof ServerLevel level)) return;
        switch (el) {
            case FIRE -> target.igniteForSeconds(3 + tier);
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + tier * 20, Math.min(3, tier / 2)));
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 60));
            }
            case VOID -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + tier * 20, tier >= 4 ? 1 : 0));
            case STORM -> {
                if (level.random.nextFloat() < 0.15f + tier * 0.05f) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.getX(), target.getY(), target.getZ());
                        level.addFreshEntity(bolt);
                    }
                }
            }
            case NATURE -> {
                if (attacker instanceof LivingEntity user) user.heal(0.5f + tier * 0.5f);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 40 + tier * 10, 0));
            }
            case GLITCH -> {
                if (level.random.nextFloat() < 0.3f) target.randomTeleport(target.getX() + level.random.nextGaussian() * 4, target.getY(),
                        target.getZ() + level.random.nextGaussian() * 4, true);
                level.sendParticles(RvParticles.GLITCH.get().with(Element.GLITCH.color, 0.5f, 14), target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
            }
            case STAR -> {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20 + tier * 8, 0));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
            }
            case ARCANE -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 + tier * 20, Math.min(2, tier / 2)));
        }
        level.sendParticles(RvParticles.SPARK.get().with(el.color, 0.5f, 16), target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                10, 0.3, 0.4, 0.3, 0.15);
    }

    public static String elementLine(ItemStack stack) {
        Element el = element(stack);
        return el.word + " • tier " + tier(stack) + " • " + switch (el) {
            case FIRE -> "sets foes ablaze";
            case FROST -> "freezes and slows";
            case VOID -> "withers what it touches";
            case STORM -> "calls down lightning";
            case NATURE -> "poisons foes, mends its wielder";
            case GLITCH -> "scrambles a target's position";
            case STAR -> "lifts foes into the sky";
            case ARCANE -> "saps strength";
        };
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
