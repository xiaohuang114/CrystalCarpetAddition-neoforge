package crystal0404.crystalcarpetaddition.utils.annotation.mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 类级限制注解，复刻 me.fallenbreath.conditionalmixin.api.annotation.Restriction（0.6.4）。
 * <p>
 * 语义：require 全部为真才应用；conflict 任一为真则不应用。
 * <p>
 * 注意：与 {@code crystal0404.crystalcarpetaddition.utils.annotation.Restriction}（{@code @Target(FIELD)}，
 * 给 CCASettings 的规则字段用）是两个不同的注解类。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Restriction {
    Condition[] require() default {};

    Condition[] conflict() default {};
}
