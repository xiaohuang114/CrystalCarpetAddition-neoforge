package crystal0404.crystalcarpetaddition.mixins.rule.MagicBox;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(require = {@Condition(value = "minecraft", versionPredicates = {">=1.21.1"})})
@Mixin(LecternBlock.class)
public abstract class LecternBlockMixin extends BaseEntityBlock {
    protected LecternBlockMixin(Properties properties) {
        super(properties);
    }

    @WrapWithCondition(
            method = "onRemove",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/BaseEntityBlock;onRemove(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private boolean removeOnRemoveMixin(BaseEntityBlock instance, BlockState blockState, Level level,
                                        BlockPos blockPos, BlockState newState, boolean moved) {
        return !CCASettings.MagicBox;
    }

    @Inject(method = "onRemove", at = @At("TAIL"))
    private void afterOnRemoveMixin(BlockState state, Level level, BlockPos pos, BlockState newState,
                                    boolean moved, CallbackInfo ci) {
        if (CCASettings.MagicBox) {
            super.onRemove(state, level, pos, newState, moved);
        }
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/LecternBlockMixin.java (s6)
