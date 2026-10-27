package crystal0404.crystalcarpetaddition.mixins.rule.ReIntroduceOldVersionRaid;

import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.BadOmenMobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        require = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(
                value = "minecraft",
                versionPredicates = {">=1.20.5"}
        )
)
@Mixin(BadOmenMobEffect.class)
public abstract class BadOmenStatusEffectMixin {
    @Inject(
            method = "applyEffectTick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void applyUpdateEffectMixin(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (!CCASettings.ReIntroduceOldVersionRaid) {
            return;
        }
        if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.isSpectator()) {
            cir.setReturnValue(this.cca$tryStartRaid(serverPlayer, serverPlayer.serverLevel()));
        } else {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean cca$tryStartRaid(ServerPlayer player, ServerLevel world) {
        BlockPos pos = player.blockPosition();
        if (world.getDifficulty() != Difficulty.PEACEFUL && world.isVillage(pos)) {
            return world.getRaids().createOrExtendRaid(player, pos) == null;
        }
        return true;
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/BadOmenStatusEffectMixin.java (s8)
