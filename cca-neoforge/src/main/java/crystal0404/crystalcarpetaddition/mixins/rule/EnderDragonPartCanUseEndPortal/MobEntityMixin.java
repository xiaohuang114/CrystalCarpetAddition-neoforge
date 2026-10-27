package crystal0404.crystalcarpetaddition.mixins.rule.EnderDragonPartCanUseEndPortal;

import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/EnderDragonPartCanUseEndPortal/MobEntityMixin.java (s5)
@Mixin(Mob.class)
public abstract class MobEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tickMixin(CallbackInfo ci) {
        if (!CCASettings.EnderDragonPartCanUseEndPortal) {
            return;
        }
        if (!((Object) this instanceof EnderDragon dragon)) {
            return;
        }
        for (EnderDragonPart part : dragon.getSubEntities()) {
            PortalProcessor portalProcessor = ((EntityAccessor) part).getPortalManager();
            if (portalProcessor == null) {
                continue;
            }
            Portal portal = ((PortalManagerAccessor) portalProcessor).getPortal();
            if (portal instanceof EndPortalBlock) {
                ((EntityInvoker) part).invokeTickPortalTeleportation();
            }
        }
    }
}
