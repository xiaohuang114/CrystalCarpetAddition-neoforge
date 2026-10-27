package crystal0404.crystalcarpetaddition.mixins.rule.EndermanCannotPickUpBlocksInNether;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/EndermanCannotPickUpBlocksInNether/EndermanEntity$PickUpBlockGoalMixin.java (s5)
@Mixin(EnderMan.EndermanTakeBlockGoal.class)
public abstract class EndermanEntity$PickUpBlockGoalMixin {
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"
            )
    )
    private boolean tickMixin(boolean original, @Local(ordinal = 0) Level level) {
        return CCASettings.EndermanCannotPickUpBlocksInNether
                ? level.dimension() != Level.NETHER && original
                : original;
    }
}
