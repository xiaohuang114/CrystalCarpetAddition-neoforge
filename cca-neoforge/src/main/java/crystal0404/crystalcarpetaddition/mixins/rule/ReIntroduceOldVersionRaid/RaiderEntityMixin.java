package crystal0404.crystalcarpetaddition.mixins.rule.ReIntroduceOldVersionRaid;

import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        require = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(
                value = "minecraft",
                versionPredicates = {">=1.20.5"}
        )
)
@Mixin(Raider.class)
public abstract class RaiderEntityMixin extends PatrollingMonster {
    protected RaiderEntityMixin(EntityType<? extends PatrollingMonster> entityType, Level world) {
        super(entityType, world);
    }

    @Shadow
    @Nullable
    public abstract Raid getCurrentRaid();

    @Inject(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/PatrollingMonster;die(Lnet/minecraft/world/damagesource/DamageSource;)V"
            )
    )
    private void onDeathMixin(DamageSource damageSource, CallbackInfo ci) {
        // 注入点不是方法边界，世界类型要自己再判一次
        if (!CCASettings.ReIntroduceOldVersionRaid || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.isPatrolLeader()
                && this.getCurrentRaid() == null
                && serverLevel.getRaidAt(this.blockPosition()) == null) {
            ItemStack itemStack = this.getItemBySlot(EquipmentSlot.HEAD);
            Player player = this.cca$getPlayerEntity(damageSource.getEntity());
            if (!itemStack.isEmpty() && this.cca$hasBanner(itemStack) && player != null) {
                MobEffectInstance old = player.getEffect(MobEffects.BAD_OMEN);
                int level = 1;
                if (old != null) {
                    level += old.getAmplifier();
                    player.removeEffectNoUpdate(MobEffects.BAD_OMEN);
                } else {
                    level--;
                }
                MobEffectInstance instance = new MobEffectInstance(
                        MobEffects.BAD_OMEN,
                        120000,
                        Mth.clamp(level, 0, 4),
                        false,
                        false,
                        true
                );
                if (!serverLevel.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                    player.addEffect(instance);
                }
            }
        }
    }

    @Unique
    @Nullable
    private Player cca$getPlayerEntity(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        } else if (entity instanceof Wolf wolf) {
            LivingEntity owner = wolf.getOwner();
            if (wolf.isTame() && owner instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @Unique
    private boolean cca$hasBanner(ItemStack itemStack) {
        return ItemStack.isSameItemSameComponents(
                itemStack,
                Raid.getLeaderBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))
        );
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/ReIntroduceOldVersionRaid/RaiderEntityMixin.java (s8)
