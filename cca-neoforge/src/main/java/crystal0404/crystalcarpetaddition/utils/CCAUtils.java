package crystal0404.crystalcarpetaddition.utils;

import crystal0404.crystalcarpetaddition.CrystalCarpetAdditionMod;
import crystal0404.crystalcarpetaddition.utils.condition.ConditionTester;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.spongepowered.asm.service.MixinService;

/**
 * ported from _decomp/mojmap/crystal0404/crystalcarpetaddition/utils/CCAUtils.java (s3)
 * <p>
 * s2 的最小版被本文件整体替换：新增 JAVA_PARAMETERS / isLoad(String,String...) / tryFindClass /
 * DisableEasterEggs / EnableMagicSetting，并把版本谓词判定改成 NeoForge 侧自实现（原库走 Fabric 的
 * VersionPredicateParser，NeoForge 没有）。
 */
public final class CCAUtils {
    /**
     * 三个 -D 开关。注意 Key 里存的是“语义值”，不是原始字符串：
     * <ul>
     *   <li>{@code cca.disable.EasterEggs}：存的是 <b>“彩蛋被禁用”</b>（默认 true = 默认禁用）。
     *       传 {@code -Dcca.disable.EasterEggs=false} 才会把 value 变 false。</li>
     *   <li>{@code cca.enable.debug}：存的是 debug 开关，默认 false。</li>
     *   <li>{@code cca.enable.MagicSettings}：存的是 MagicSettings 开关，默认 false。</li>
     * </ul>
     */
    public static final Map<String, Key> JAVA_PARAMETERS = Map.of(
            "cca.disable.EasterEggs", new Key(!isFalse("cca.disable.EasterEggs"), true),
            "cca.enable.debug", new Key(Boolean.getBoolean("cca.enable.debug"), false),
            "cca.enable.MagicSettings", new Key(Boolean.getBoolean("cca.enable.MagicSettings"), false));

    static {
        // 原版行为：-D 值与默认值不一致时打一条 warn，方便定位“为什么行为不一样”
        JAVA_PARAMETERS.forEach((key, value) -> {
            if (value.getValue() != value.getDefaultValue()) {
                CrystalCarpetAdditionMod.LOGGER.warn("[CCA] -D{}={}", key, value.getValue());
            }
        });
    }

    private CCAUtils() {
    }

    public static boolean isFalse(String name) {
        try {
            return "false".equalsIgnoreCase(System.getProperty(name));
        } catch (NullPointerException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isEnableDebug() {
        return Objects.requireNonNull(JAVA_PARAMETERS.get("cca.enable.debug")).getValue();
    }

    /**
     * 原库用它探测“某个类在不在”，与 mixin 判定无关，走的是 Mixin 的字节码服务，不加载类。
     */
    public static boolean tryFindClass(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IOException e) {
            CrystalCarpetAdditionMod.LOGGER.error("[CCA] An unknown exception occurred while trying to find the class {}", className, e);
            throw new RuntimeException(e);
        }
    }

    /** 空数组 = 只判 mod 存在；非空 = 任一谓词满足即可。 */
    public static boolean isLoad(String modId, String... versionPredicates) {
        if (modId == null || modId.isEmpty()) {
            return false;
        }
        String actual;
        try {
            if (ModIds.MC.equals(modId)) {
                // minecraft 在 NeoForge 里是伪 mod，走与其它 mod 相同的双通道查询（LoadingModList / ModList）。
                // 不能只看 SharedConstants：mixin config 解析期它抛 IllegalStateException("Game version not set")，
                // 实测见 _tools/out/s6_diag1.log（isLoad(minecraft) threw ...），会让整条 require 判成 false 而 SKIP。
                actual = lookupModVersion(ModIds.MC);
                if (actual == null || actual.isBlank()) {
                    // 兜底：游戏完成 bootstrap 之后 SharedConstants 才是可靠的（规则注册阶段走这里）
                    actual = SharedConstants.getCurrentVersion().getName();
                }
            } else {
                actual = lookupModVersion(modId);
                if (actual == null) {
                    return false;
                }
            }
        } catch (Throwable t) {
            CrystalCarpetAdditionMod.LOGGER.warn("[CCA] isLoad({}) could not resolve version: {}", modId, t.toString());
            return false;
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        if (versionPredicates == null || versionPredicates.length == 0) {
            return true;
        }
        for (String predicate : versionPredicates) {
            try {
                if (matches(actual, predicate)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 单个谓词写错不影响其它谓词，与原库“判不出就当不满足”一致
                CrystalCarpetAdditionMod.LOGGER.warn("[CCA] bad version predicate '{}' for {}={}", predicate, modId, actual);
            }
        }
        return false;
    }

    /** s2 的单串版调用点保持不变，委托给数组版。 */
    public static boolean isLoad(String modId, String versionPredicate) {
        return isLoad(modId, new String[]{versionPredicate});
    }

    /**
     * 取某个 modId 的版本字符串；mod 不存在返回 {@code null}。
     * <p>
     * <b>必须双通道</b>：mixin 的 {@code shouldApplyMixin} 在 mixin config 解析期被调用，那时 FML 的
     * {@link ModList} 还没构造（实测 {@code ModList.get() == null}，见 _tools/out/s3_runB2.log 的
     * {@code [CCA][TEMP] isLoad(carpet) ModList==null}），只能查启动期的
     * {@code LoadingModList}。等到规则注册阶段（mod 构造器里）ModList 才可用，那时走第一通道。
     */
    private static String lookupModVersion(String modId) {
        ModList modList = ModList.get();
        if (modList != null) {
            var container = modList.getModContainerById(modId);
            return container.isEmpty() ? null : container.get().getModInfo().getVersion().toString();
        }
        // 启动期：LoadingModList 由 FMLLoader 在 launch 前建好，mixin config 读它时一定已存在
        LoadingModList loading = LoadingModList.get();
        if (loading == null) {
            return null;
        }
        var fileInfo = loading.getModFileById(modId);
        if (fileInfo == null) {
            return null;
        }
        for (IModInfo mod : fileInfo.getMods()) {
            if (modId.equals(mod.getModId())) {
                return mod.getVersion().toString();
            }
        }
        return fileInfo.versionString();
    }

    /**
     * 版本谓词判定。
     * <p>
     * <b>不能用 {@code VersionRange.createFromVersionSpec(">=1.21.4")}</b>：Maven 的 spec 解析把不带方括号的字符串
     * 当成“推荐版本”而不是约束，{@code containsVersion} 恒返回 true —— 实测 1.21.1 对 ">=1.21.4" 也判真，
     * 那 I12 期望的“RemoveVillagerTradeDistanceLimit 掉出列表”就永远不会发生。所以软约束（{@code >=} 等）
     * 自己比较 {@link DefaultArtifactVersion}，只有真正带括号的 Maven 区间才交给 {@link VersionRange}。
     */
    private static boolean matches(String actualVersion, String predicate) throws Exception {
        String spec = predicate == null ? "" : predicate.trim();
        if (spec.isEmpty() || "*".equals(spec)) {
            return true;
        }
        DefaultArtifactVersion actual = new DefaultArtifactVersion(actualVersion);
        if (spec.startsWith("[") || spec.startsWith("(")) {
            return VersionRange.createFromVersionSpec(spec).containsVersion(actual);
        }

        String op = "";
        String rest = spec;
        for (String candidate : new String[]{">=", "<=", "!=", "==", ">", "<", "="}) {
            if (spec.startsWith(candidate)) {
                op = candidate;
                rest = spec.substring(candidate.length()).trim();
                break;
            }
        }
        if (rest.isEmpty()) {
            return true;
        }
        // 通配符版本（如 "1.21.x" / "1.21*"）按前缀匹配
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.indexOf('x') >= 0 || rest.indexOf('*') >= 0) {
            String prefix = rest.replaceAll("[xX*].*$", "");
            return actualVersion.startsWith(prefix);
        }

        int cmp = actual.compareTo(new DefaultArtifactVersion(rest));
        return switch (op) {
            case ">=" -> cmp >= 0;
            case ">" -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<" -> cmp < 0;
            case "!=" -> cmp != 0;
            default -> cmp == 0; // "=", "==", 无前缀（精确匹配）
        };
    }

    /**
     * 彩蛋开关测试器，用于 mixin 的 conflict。
     * <p>
     * <b>这里必须直接返回 {@code getValue()}，不能取反</b>：{@code cca.disable.EasterEggs} 这个 Key 存的就是
     * “彩蛋被禁用”（默认 true）。返回值 = true 表示“彩蛋被禁用” → conflict 成立 → 该 mixin 不注入。
     * 取反会让默认配置下彩蛋 mixin 反而被注入，与 Fabric 原版行为相反。
     */
    public static final class DisableEasterEggs implements ConditionTester {
        @Override
        public boolean isSatisfied(String mixinClassName) {
            return Objects.requireNonNull(JAVA_PARAMETERS.get("cca.disable.EasterEggs")).getValue();
        }
    }

    /** 同时实现 ConditionTester（mixin 门控）与 carpet 的 Rule.Condition（规则注册门控）。 */
    public static final class EnableMagicSetting implements ConditionTester, carpet.api.settings.Rule.Condition {
        @Override
        public boolean isSatisfied(String mixinClassName) {
            return Objects.requireNonNull(JAVA_PARAMETERS.get("cca.enable.MagicSettings")).getValue();
        }

        @Override
        public boolean shouldRegister() {
            return Objects.requireNonNull(JAVA_PARAMETERS.get("cca.enable.MagicSettings")).getValue();
        }
    }
}
