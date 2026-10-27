package crystal0404.crystalcarpetaddition.mixins.carpet;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.SettingsManager;
import carpet.utils.Messenger;
import carpet.utils.Translations;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import crystal0404.crystalcarpetaddition.CCAExtension;
import crystal0404.crystalcarpetaddition.CrystalCarpetAdditionMod;
import crystal0404.crystalcarpetaddition.utils.annotation.Restriction;
import crystal0404.crystalcarpetaddition.utils.annotation.impl.AnnotationProcessor;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/carpet/SettingsManagerMixin.java (s4)
// 注：Restriction(x2) 是同名不同包的两个注解 —— 类级条件用 utils.annotation.mixin.Restriction，
// 字段级条件（规则注册）用 utils.annotation.Restriction。两者都要用，别搞混。
@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        require = @Condition("carpet")
)
@Mixin(SettingsManager.class)
public abstract class SettingsManagerMixin {
    @Shadow
    @Final
    private String fancyName;

    @Inject(
            method = "listAllSettings",
            at = @At(
                    value = "INVOKE",
                    target = "Lcarpet/utils/Messenger;m(Lnet/minecraft/commands/CommandSourceStack;[Ljava/lang/Object;)V",
                    ordinal = 0,
                    shift = Shift.AFTER
            )
    )
    private void listAllSettingsMixin(CommandSourceStack source, CallbackInfoReturnable<Integer> cir) {
        if (Objects.equals(this.fancyName, "Carpet Mod")) {
            String msg = "g %s %s: %s".formatted(
                    "Crystal Carpet Addition",
                    Translations.tr("carpet.settings.command.version"),
                    CrystalCarpetAdditionMod.version);
            Messenger.m(source, new Object[]{msg});
        }
    }

    @WrapWithCondition(
            method = "parseSettingsClass",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private boolean parseSettingsClassMixin_shouldRegister(
            Map<String, CarpetRule<?>> instance,
            @Coerce Object k,
            @Coerce Object v,
            @Local(ordinal = 0) Field field
    ) {
        // 反编译产物丢了类型转换：this 的静态类型是 SettingsManagerMixin，与 SettingsManager 无继承关系，
        // javac 会报“不可比较的类型”。运行时 this 就是被织入的 SettingsManager 实例，按对象身份比较。
        if ((Object) this == CCAExtension.CCASettingsManager) {
            Restriction restriction = field.getAnnotation(Restriction.class);
            if (restriction != null) {
                return AnnotationProcessor.shouldRegister(restriction, (String) k);
            }
        }
        return true;
    }
}
