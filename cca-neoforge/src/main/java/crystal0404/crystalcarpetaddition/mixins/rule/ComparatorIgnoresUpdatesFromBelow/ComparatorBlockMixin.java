package crystal0404.crystalcarpetaddition.mixins.rule.ComparatorIgnoresUpdatesFromBelow;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.level.block.ComparatorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ComparatorIgnoresUpdatesFromBelow/ComparatorBlockMixin.java (s5)
@Mixin(ComparatorBlock.class)
public abstract class ComparatorBlockMixin {
    @ModifyExpressionValue(
            method = "updateShape",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/ComparatorBlock;canSurviveOn(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"
            )
    )
    private boolean getStateForNeighborUpdateMixin(boolean original) {
        return CCASettings.ComparatorIgnoresStateUpdatesFromBelow || original;
    }
}
