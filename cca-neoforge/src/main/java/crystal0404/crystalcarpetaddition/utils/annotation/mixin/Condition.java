package crystal0404.crystalcarpetaddition.utils.annotation.mixin;

import crystal0404.crystalcarpetaddition.utils.condition.ConditionTester;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 类级条件注解，复刻 me.fallenbreath.conditionalmixin.api.annotation.Condition（0.6.4）。
 * <p>
 * 注意：与 {@code crystal0404.crystalcarpetaddition.utils.annotation.Condition}（FIELD 级、给 CCASettings 的规则字段用、
 * {@code versionPredicates()} 是单个 String）是两个不同的注解类，不要互相 import 错。
 * 这里的 {@code versionPredicates()} 必须是 {@code String[]}：
 * CCASettings 里既有 {@code versionPredicates = ">=1.21"}（单值自动成 1 元数组），后续 mixin 也会有 {@code {">=1.21.1"}}。
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Condition {
    Type type() default Type.MOD;

    String value() default "";

    String[] versionPredicates() default {};

    /** 默认值是接口本身，判定时靠 {@code clazz.isInterface()} 识别“没填”。 */
    Class<? extends ConditionTester> tester() default ConditionTester.class;

    enum Type {
        MOD,
        MIXIN,
        TESTER
    }
}
