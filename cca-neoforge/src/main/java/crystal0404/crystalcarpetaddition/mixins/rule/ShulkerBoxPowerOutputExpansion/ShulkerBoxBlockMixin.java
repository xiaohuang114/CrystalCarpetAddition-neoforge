package crystal0404.crystalcarpetaddition.mixins.rule.ShulkerBoxPowerOutputExpansion;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.shulkerBoxUtils.ColourMap;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShulkerBoxBlock.class)
public abstract class ShulkerBoxBlockMixin {
    @WrapOperation(
            method = "getAnalogOutputSignal",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;getRedstoneSignalFromBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;)I"
            )
    )
    private int getComparatorOutputMixin(BlockEntity blockEntity, Operation<Integer> original) {
        if (!CCASettings.ShulkerBoxPowerOutputExpansion) {
            return original.call(blockEntity);
        }
        if (blockEntity instanceof ShulkerBoxBlockEntity shulkerBox
                && shulkerBox.getColor() == ColourMap.getSettingColour()) {
            int filled = 0;
            for (int i = 0; filled != 15 && i < shulkerBox.getContainerSize(); i++) {
                if (!shulkerBox.getItem(i).isEmpty()) {
                    filled++;
                }
            }
            return filled;
        }
        return original.call(blockEntity);
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ShulkerBoxPowerOutputExpansion/ShulkerBoxBlockMixin.java (s7)
