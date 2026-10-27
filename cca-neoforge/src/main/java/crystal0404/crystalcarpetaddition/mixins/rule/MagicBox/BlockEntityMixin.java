package crystal0404.crystalcarpetaddition.mixins.rule.MagicBox;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = {@Condition(value = "minecraft", versionPredicates = {">=1.21.1"})})
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @WrapWithCondition(
            method = "<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;validateBlockState(Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private boolean initMixin(BlockEntity instance, BlockState blockState) {
        return !CCASettings.MagicBox || !(instance instanceof LecternBlockEntity);
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/BlockEntityMixin.java (s6)
