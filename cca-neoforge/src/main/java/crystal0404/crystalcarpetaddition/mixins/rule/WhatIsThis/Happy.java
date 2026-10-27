package crystal0404.crystalcarpetaddition.mixins.rule.WhatIsThis;

import com.llamalad7.mixinextras.sugar.Local;
import crystal0404.crystalcarpetaddition.utils.CCAUtils;
import java.util.Calendar;
import java.util.Random;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WhatIsThis · 彩蛋：3 月 25 日 1/3 概率把 splash 换成生日祝福。
 * <p>
 * 与上游的两处必要差异（不是"翻译成相似的东西"，是基准导致的）：
 * <ol>
 *   <li>{@code method = "get"} → {@code "getSplash"}：Fabric 1.21.1 发行 jar 经 Mojmap 重映射后，源码里的
 *       {@code get} 就是 {@code SplashManager#getSplash()}（见 {@code _mappings/refmap_mojmap.md} 该 mixin 唯一一行，
 *       以及 {@code javap} 的 {@code public net.minecraft.client.gui.components.SplashRenderer getSplash()}）。</li>
 *   <li>{@code calendar.get(2) == 2 && calendar.get(5) == 25} → {@code Calendar.MONTH == Calendar.MARCH
 *       && Calendar.DAY_OF_MONTH == 25}：语义完全等价（{@code get(2)} 的 2 就是 0 基 MONTH = 三月），
 *       避免后人再把它读成"12 月 25 日"（s8 勘误 N12 记的就是这个误读）。</li>
 * </ol>
 * 类级条件 {@code @Restriction(conflict = …)} 的**方向不能反**：{@code DisableEasterEggs.isSatisfied} 直接返回
 * "彩蛋被禁用"这个值（默认 true），放 conflict 里意味着"默认禁用 → mixin 不应用"，
 * 传 {@code -Dcca.disable.EasterEggs=false} 才应用（s8 勘误 N9）。
 * <p>
 * 本类只能进 {@code cca.mixins.json} 的 {@code client} 数组：{@code SplashManager} 是 client-only 类，
 * 放进 {@code mixins} 会让专用服务端 {@code NoClassDefFoundError}。
 */
@crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction(
        conflict = @crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition(
                type = crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition.Type.TESTER,
                tester = CCAUtils.DisableEasterEggs.class
        )
)
@Mixin(SplashManager.class)
public abstract class Happy {
    @Inject(
            method = "getSplash",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Calendar;setTime(Ljava/util/Date;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void happyMixin(CallbackInfoReturnable<SplashRenderer> cir, @Local(ordinal = 0) Calendar calendar) {
        // 3 月 25 日：Calendar.MONTH 是 0 基，MARCH == 2；DAY_OF_MONTH == 25
        if (calendar.get(Calendar.MONTH) == Calendar.MARCH && calendar.get(Calendar.DAY_OF_MONTH) == 25) {
            Random random = new Random();
            if (random.nextInt(3) == 2) {
                cir.setReturnValue(new SplashRenderer("Happy birthday Crystal0404!!!"));
            }
        }
    }
}
// ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/mixins/rule/WhatIsThis/Happy.java (s9)
