package crystal0404.crystalcarpetaddition.mixins.rule.StackableShulkerBoxesEnhancement;

import carpet.CarpetSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import crystal0404.crystalcarpetaddition.CCASettings;
import crystal0404.crystalcarpetaddition.utils.shulkerBoxUtils.ShulkerBoxesSet;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge 专属补偿 mixin —— 上游（Fabric）没有这个类。
 *
 * <h2>为什么需要它</h2>
 * 上游 {@code HopperBlockEntityMixin} 用 {@code @WrapOperation} 包 {@code tryMoveInItem} 里的
 * {@code canMergeItems} 来实现"禁止漏斗合并潜影盒"。这条规则在 Fabric 上成立，因为漏斗吐物品走的是
 * vanilla 路径：{@code ejectItems → addItem → tryMoveInItem → canMergeItems}。
 *
 * <p>但 NeoForge 在 {@code HopperBlockEntity.ejectItems} 的**最开头**插了一段能力派发：
 * <pre>
 * 35: aload_2
 * 36: invokestatic net/neoforged/neoforge/items/VanillaInventoryCodeHooks.insertHook(HopperBlockEntity)Z
 * 39: ifeq 44
 * 42: iconst_1
 * 43: ireturn          ← 目标容器暴露 IItemHandler 能力时从这里返回
 * …
 * 123: invokestatic addItem(Container, Container, ItemStack, Direction)   ← 不可达
 * </pre>
 * NeoForge 给所有原版 {@code Container}（箱子、漏斗…）都挂了 {@code IItemHandler} capability，
 * 于是 {@code insertHook} 返回 {@code true}、{@code ejectItems} 直接 {@code ireturn}，
 * vanilla 的 {@code addItem → tryMoveInItem → canMergeItems} **整段被跳过** ——
 * 上游那条 wrap 在 NeoForge 上**永远没有调用点**。
 *
 * <h2>这个 mixin 做什么</h2>
 * 在同一个调用点上 wrap {@code insertHook}：当
 * ① 规则 {@code StackableShulkerBoxesEnhancement} 开启、
 * ② {@code CarpetSettings.shulkerBoxStackSize != 1}（carpet 的潜影盒堆叠已开）、
 * ③ 漏斗里确实有潜影盒、
 * ④ 目标方块实体是 vanilla {@code Container}（保证 vanilla 路径走得通）
 * 四条同时成立时返回 {@code false}，让 {@code ejectItems} 继续走 vanilla 路径，
 * 从而让上游的 {@code transferMixin}（包 {@code canMergeItems}）重新拿到执行机会。
 *
 * <p>条件 ④ 是关键：若目标是"只暴露 {@code IItemHandler}、不是 {@code Container}"的 mod 容器，
 * vanilla 的 {@code addItem} 无法处理，此时必须保持原样（返回 {@code true}）走能力派发，
 * 否则会把原本能用的传输弄坏。
 *
 * <p>类级条件与上游 {@code HopperBlockEntityMixin} 保持一致：装了 {@code pca} 就整体跳过。
 */
@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        conflict = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition("pca")
)
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityEjectMixin {

    /**
     * 在 {@code ejectItems} 调用 {@code VanillaInventoryCodeHooks.insertHook} 的位置拦截。
     * 返回 {@code false} 表示"不要让能力派发接管"，把控制权交还 vanilla 的 {@code addItem} 路径。
     */
    @WrapOperation(
            method = "ejectItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/items/VanillaInventoryCodeHooks;insertHook(Lnet/minecraft/world/level/block/entity/HopperBlockEntity;)Z"
            )
    )
    private static boolean cca$fallbackToVanillaPath(HopperBlockEntity hopper, Operation<Boolean> original) {
        // ⚠ 顺序至关重要：original.call(hopper) 内部会真的把物品从漏斗搬进目标容器，
        // 一旦先调用它，再检查 hopper.getItem(i) 必然为空、永远判成"没有潜影盒"。
        // 所以必须先判条件，需要回退时**根本不调用** original。
        if (cca$shouldFallbackToVanilla(hopper)) {
            return false;
        }
        return original.call(hopper);
    }

    /**
     * 四条同时成立才回退：规则开、carpet 堆叠已开、漏斗里确实有潜影盒、目标方块实体是 vanilla {@code Container}。
     * <p>必须在不触碰 {@code Operation} 的前提下判断。
     */
    private static boolean cca$shouldFallbackToVanilla(HopperBlockEntity hopper) {
        if (!CCASettings.StackableShulkerBoxesEnhancement || CarpetSettings.shulkerBoxStackSize == 1) {
            return false;
        }

        final Level level = hopper.getLevel();
        if (level == null) {
            return false;
        }

        final BlockState state = hopper.getBlockState();
        if (!state.hasProperty(HopperBlock.FACING)) {
            return false;
        }

        final Direction facing = state.getValue(HopperBlock.FACING);
        final BlockEntity target = level.getBlockEntity(hopper.getBlockPos().relative(facing));

        // 目标不是 vanilla Container（例如只暴露 IItemHandler 的 mod 容器）—— 不能回退
        if (!(target instanceof Container)) {
            return false;
        }

        final int size = hopper.getContainerSize();
        for (int i = 0; i < size; i++) {
            final ItemStack stack = hopper.getItem(i);
            if (!stack.isEmpty() && ShulkerBoxesSet.ITEMS_SET.contains(stack.getItem())) {
                return true;
            }
        }

        return false;
    }
}

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/StackableShulkerBoxesEnhancement/HopperBlockEntityMixin.java (s9, NeoForge 补偿)
