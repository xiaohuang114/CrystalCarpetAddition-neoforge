package crystal0404.crystalcarpetaddition.mixins.rule.MagicBox;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShulkerBoxBlock.class)
public abstract class ShulkerBoxBlockMixin {
    @WrapOperation(
            method = "getAnalogOutputSignal",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getRedstoneSignalFromBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)I"))
    private int getAnalogOutputSignalMixin(BlockEntity blockEntity, Operation<Integer> original) {
        return CCASettings.MagicBox
                ? AbstractContainerMenu.getRedstoneSignalFromContainer((Container) blockEntity)
                : original.call(blockEntity);
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/MagicBox/ShulkerBoxBlockMixin.java (s6)
