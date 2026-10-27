package crystal0404.crystalcarpetaddition.mixins.rule.EnderDragonPartCanUseEndPortal;

import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/EnderDragonPartCanUseEndPortal/PortalManagerAccessor.java (s5)
@Mixin(PortalProcessor.class)
public interface PortalManagerAccessor {
    @Accessor("portal")
    Portal getPortal();
}
