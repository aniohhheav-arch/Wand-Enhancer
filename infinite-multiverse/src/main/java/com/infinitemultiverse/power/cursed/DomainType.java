package com.infinitemultiverse.power.cursed;

import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/** The three Domain Expansions: barrier, floor, colour and the sure-hit rule each one enforces (see DomainManager). */
public enum DomainType {
    UNLIMITED_VOID("unlimited_void", 0x3D7BFF, Blocks.TINTED_GLASS, Blocks.BLACK_CONCRETE, Blocks.SEA_LANTERN, 1.0, BossEvent.BossBarColor.BLUE),
    MALEVOLENT_SHRINE("malevolent_shrine", 0xD0102A, null, Blocks.RED_CONCRETE, Blocks.BONE_BLOCK, 1.5, BossEvent.BossBarColor.RED),
    CHIMERA_SHADOW_GARDEN("chimera_shadow_garden", 0x2A2A46, Blocks.BLACK_STAINED_GLASS, Blocks.BLACK_CONCRETE, Blocks.CRYING_OBSIDIAN, 1.0, BossEvent.BossBarColor.PURPLE);

    private final String id;
    private final int color;
    @Nullable
    private final Block shell;
    private final Block floor;
    private final Block accent;
    private final double radiusScale;
    private final BossEvent.BossBarColor barColor;

    DomainType(String id, int color, @Nullable Block shell, Block floor, Block accent, double radiusScale, BossEvent.BossBarColor barColor) {
        this.id = id;
        this.color = color;
        this.shell = shell;
        this.floor = floor;
        this.accent = accent;
        this.radiusScale = radiusScale;
        this.barColor = barColor;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    /** Null for open domains without a barrier (Malevolent Shrine). */
    @Nullable
    public Block shell() {
        return shell;
    }

    public Block floor() {
        return floor;
    }

    public Block accent() {
        return accent;
    }

    public double radiusScale() {
        return radiusScale;
    }

    public BossEvent.BossBarColor barColor() {
        return barColor;
    }
}
