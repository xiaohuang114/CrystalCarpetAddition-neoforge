package crystal0404.crystalcarpetaddition.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public class LootTableUtils {
    public static LootTable Witch(RegistryLookup<Enchantment> impl) {
        Holder<Enchantment> enchantment = impl.getOrThrow(Enchantments.LOOTING);
        LootPool.Builder loot = LootPool.lootPool()
                .setBonusRolls(ConstantValue.exactly(0.0F))
                .add(
                        LootItem.lootTableItem(Items.GLOWSTONE_DUST)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.SUGAR)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.REDSTONE)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.SPIDER_EYE)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.GLASS_BOTTLE)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.GUNPOWDER)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .add(
                        LootItem.lootTableItem(Items.STICK)
                                .setWeight(2)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(new EnchantedCountIncreaseFunction.Builder(
                                        enchantment, UniformGenerator.between(0.0F, 1.0F)))
                )
                .setRolls(UniformGenerator.between(1.0F, 3.0F));
        return LootTable.lootTable().withPool(loot).build(); // ported from _decomp/mojmap/... (s2)
    }
}
