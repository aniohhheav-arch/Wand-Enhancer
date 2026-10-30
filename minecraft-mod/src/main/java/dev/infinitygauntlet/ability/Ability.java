package dev.infinitygauntlet.ability;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A server-authoritative gauntlet ability. Implementations perform all gameplay on the server and request visuals through {@code Vfx}. */
public abstract class Ability {
    private final ResourceLocation id;
    private final InfinityStone stone;
    private final int defaultCooldown;

    protected Ability(String name, InfinityStone stone, int defaultCooldown) {
        this.id = InfinityGauntlet.id(name);
        this.stone = stone;
        this.defaultCooldown = defaultCooldown;
    }

    public abstract CastResult cast(AbilityContext ctx);

    public ResourceLocation id() {
        return id;
    }

    public InfinityStone stone() {
        return stone;
    }

    public int defaultCooldown() {
        return defaultCooldown;
    }

    public String translationKey() {
        return "ability.infinitygauntlet." + id.getPath();
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    public Component description() {
        return Component.translatable(translationKey() + ".desc");
    }
}
