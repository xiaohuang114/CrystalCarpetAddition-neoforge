package crystal0404.crystalcarpetaddition.mixins.rule.ReIntroduceOldVersionWitchLootTable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.LootTableUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapOperation(
            method = "dropFromLootTable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/ReloadableServerRegistries$Holder;getLootTable(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/storage/loot/LootTable;"
            )
    )
    private LootTable dropLootMixin(ReloadableServerRegistries.Holder holder,
                                    ResourceKey<LootTable> key,
                                    Operation<LootTable> original) {
        if ((Object) this instanceof Witch && CCASettings.ReIntroduceOldVersionWitchLootTable) {
            HolderLookup.RegistryLookup<Enchantment> lookup =
                    this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            return LootTableUtils.Witch(lookup);
        }
        return original.call(holder, key);
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionWitchLootTable/LivingEntityMixin.java (s7)
