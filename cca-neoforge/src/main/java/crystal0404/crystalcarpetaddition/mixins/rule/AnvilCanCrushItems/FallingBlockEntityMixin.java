package crystal0404.crystalcarpetaddition.mixins.rule.AnvilCanCrushItems;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import java.util.function.Predicate;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/AnvilCanCrushItems/FallingBlockEntityMixin.java (s5)
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    @Shadow
    private BlockState blockState;

    @WrapOperation(
            method = "causeFallDamage",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Predicate;and(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;"
            )
    )
    private Predicate<Entity> handleFallDamageMixin(Predicate<Entity> instance,
                                                    Predicate<? super Entity> other,
                                                    Operation<Predicate<Entity>> original) {
        return CCASettings.AnvilCanCrushItemEntities && this.blockState.is(BlockTags.ANVIL)
                ? EntitySelector.NO_CREATIVE_OR_SPECTATOR
                : original.call(instance, other);
    }
}
