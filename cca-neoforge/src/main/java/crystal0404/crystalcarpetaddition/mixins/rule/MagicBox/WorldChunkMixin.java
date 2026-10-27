package crystal0404.crystalcarpetaddition.mixins.rule.MagicBox;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(require = {@Condition(value = "minecraft", versionPredicates = {">=1.21.1"})})
@Mixin(LevelChunk.class)
public abstract class WorldChunkMixin {
    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @ModifyExpressionValue(
            method = "setBlockState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BlockEntity;isValidBlockState(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean setBlockStateMixin(boolean original, @Local(ordinal = 0) BlockEntity blockEntity) {
        if (!CCASettings.MagicBox) {
            return original;
        }
        return blockEntity instanceof LecternBlockEntity
                && this.getBlockState(blockEntity.getBlockPos()).getBlock() instanceof ShulkerBoxBlock
                ? true
                : original;
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/WorldChunkMixin.java (s6)
