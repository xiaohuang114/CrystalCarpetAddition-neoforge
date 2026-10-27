package crystal0404.crystalcarpetaddition.mixins.rule.ReIntroduceOldVersionRaid;

import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        require = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(
                value = "minecraft",
                versionPredicates = {">=1.20.5"}
        )
)
@Mixin(Raid.class)
public abstract class RaidMixin {
    @ModifyArg(
            method = "absorbRaidOmen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"
            )
    )
    private Holder<MobEffect> startMixin_getStatusEffect(Holder<MobEffect> original) {
        return CCASettings.ReIntroduceOldVersionRaid ? MobEffects.BAD_OMEN : original;
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/RaidMixin.java (s8)
