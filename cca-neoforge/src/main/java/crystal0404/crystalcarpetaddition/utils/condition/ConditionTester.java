package crystal0404.crystalcarpetaddition.utils.condition;

/**
 * 对应 me.fallenbreath.conditionalmixin.api.mixin.ConditionTester（原库同名接口）。
 * 原库把 ConditionTester.class 本身当作 {@code @Condition#tester()} 的默认哨兵，
 * 所以它是接口、且不需要 NullTester。
 */
public interface ConditionTester {
    boolean isSatisfied(String mixinClassName);
}
