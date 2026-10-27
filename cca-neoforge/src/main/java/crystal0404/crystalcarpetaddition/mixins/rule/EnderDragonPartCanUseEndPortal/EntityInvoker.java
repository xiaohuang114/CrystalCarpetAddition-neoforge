package crystal0404.crystalcarpetaddition.mixins.rule.EnderDragonPartCanUseEndPortal;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/EnderDragonPartCanUseEndPortal/EntityInvoker.java (s5)
@Mixin(Entity.class)
public interface EntityInvoker {
    @Invoker("handlePortal")
    void invokeTickPortalTeleportation();
}
