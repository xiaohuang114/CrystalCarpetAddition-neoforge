package crystal0404.crystalcarpetaddition.mixins.rule.NoBatSpwan;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.entity.ambient.Bat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/NoBatSpwan/BatEntityMixin.java (s5)
@Mixin(Bat.class)
public abstract class BatEntityMixin {
    @ModifyExpressionValue(
            method = "checkBatSpawnRules",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"
            )
    )
    private static int canSpawnMixin(int original) {
        return CCASettings.NoBatSpawn ? Integer.MIN_VALUE : original;
    }
}
