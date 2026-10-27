package crystal0404.crystalcarpetaddition.mixins.rule.StackableShulkerBoxesEnhancement;

import carpet.CarpetSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import crystal0404.crystalcarpetaddition.utils.shulkerBoxUtils.ShulkerBoxesSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Restriction(
        conflict = {@Condition("pca")}
)
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @WrapOperation(
            method = "tryMoveInItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;canMergeItems(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
            )
    )
    private static boolean transferMixin(ItemStack first, ItemStack second, Operation<Boolean> original) {
        if (CCASettings.StackableShulkerBoxesEnhancement
                && CarpetSettings.shulkerBoxStackSize != 1
                && ShulkerBoxesSet.ITEMS_SET.contains(first.getItem())) {
            return false;
        }
        return original.call(first, second);
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/StackableShulkerBoxesEnhancement/HopperBlockEntityMixin.java (s7)
