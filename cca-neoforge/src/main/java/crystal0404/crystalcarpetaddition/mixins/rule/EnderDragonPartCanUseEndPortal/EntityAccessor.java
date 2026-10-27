package crystal0404.crystalcarpetaddition.mixins.rule.EnderDragonPartCanUseEndPortal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/EnderDragonPartCanUseEndPortal/EntityAccessor.java (s5)
@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("portalProcess")
    PortalProcessor getPortalManager();
}
