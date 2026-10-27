package crystal0404.crystalcarpetaddition.mixins.rule.GatewayCannotLoadingChunks;

import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.DimensionTransition.PostDimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/GatewayCannotLoadingChunks/EndGatewayBlockMixin.java (s5)
@Mixin(EndGatewayBlock.class)
public abstract class EndGatewayBlockMixin {
    @ModifyArg(
            method = "getPortalDestination",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/portal/DimensionTransition;<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;FFLnet/minecraft/world/level/portal/DimensionTransition$PostDimensionTransition;)V"
            ),
            index = 5
    )
    private PostDimensionTransition createTeleportTargetMixin(PostDimensionTransition original) {
        return CCASettings.GatewayCannotLoadingChunks ? DimensionTransition.DO_NOTHING : original;
    }
}
