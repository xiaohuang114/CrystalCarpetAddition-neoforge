package crystal0404.crystalcarpetaddition.mixins.rule.ItemShadowing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Restriction(conflict = {
        @Condition("antishadowpatch"),
        @Condition("carpet-fixes"),
        @Condition("carpet-shadow")
})
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {
    @WrapWithCondition(
            method = "doClick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 4))
    private boolean doClickWrapMixin(Slot instance, ItemStack stack) {
        return !CCASettings.ItemShadowing;
    }

    @Inject(
            method = "doClick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 1))
    private void doClickInjectMixin(int slotIndex, int button, ClickType clickType, Player player, CallbackInfo ci,
                                    @Local(ordinal = 0) Slot slot, @Local(ordinal = 0) ItemStack itemStack) {
        if (CCASettings.ItemShadowing) {
            slot.setByPlayer(itemStack);
        }
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ItemShadowing/ScreenHandlerMixin.java (s6)
