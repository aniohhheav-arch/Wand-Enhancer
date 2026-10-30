package dev.infinitygauntlet.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** A criterion trigger keyed by a string value (ability id, stone name, travel kind). */
public final class GauntletTrigger extends SimpleCriterionTrigger<GauntletTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, String value) {
        trigger(player, instance -> instance.value().map(value::equals).orElse(true));
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<String> value) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.optionalFieldOf("value").forGetter(Instance::value)
        ).apply(i, Instance::new));
    }
}
