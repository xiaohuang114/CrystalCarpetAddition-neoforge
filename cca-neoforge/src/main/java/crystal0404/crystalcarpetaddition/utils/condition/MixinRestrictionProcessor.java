package crystal0404.crystalcarpetaddition.utils.condition;

import crystal0404.crystalcarpetaddition.utils.CCAUtils;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.mixin.Restriction;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

/**
 * 复刻 me.fallenbreath.conditionalmixin.impl.SimpleRestrictionChecker：
 * 读 mixin 类上的类级 {@link Restriction} 注解，条件不满足则整个 mixin 类不应用。
 * <p>
 * 判定链（与原库 0.6.4 一致）：
 * <ol>
 *   <li>用 Mixin 的字节码服务读 {@link ClassNode}，<b>不加载</b> mixin 类；</li>
 *   <li>没有 {@link Restriction} → 应用；</li>
 *   <li>先 require（全真），再 conflict（全假）；</li>
 *   <li>任何 Throwable → 跳过该 mixin，绝不外抛把启动搞崩。</li>
 * </ol>
 * 不做原库的 AnnotationCleaner / MemorizedRestrictionChecker：本工程只有一个 mixin config，且只有 22 个 mixin。
 */
public final class MixinRestrictionProcessor {
    private static final Logger LOG = LoggerFactory.getLogger("CCA-Mixin-Restriction");

    private MixinRestrictionProcessor() {
    }

    public static boolean shouldApply(String targetClassName, String mixinClassName) {
        try {
            AnnotationNode restriction = readRestriction(mixinClassName);
            if (restriction == null) {
                return true;
            }
            // 顺序不许换：先 require 后 conflict
            for (AnnotationNode condition : conditionList(restriction, "require")) {
                if (!test(mixinClassName, condition)) {
                    LOG.info("[CCA-Mixin] SKIP {} (require failed: {})", mixinClassName, describe(condition));
                    return false;
                }
            }
            for (AnnotationNode condition : conditionList(restriction, "conflict")) {
                if (test(mixinClassName, condition)) {
                    LOG.info("[CCA-Mixin] SKIP {} (conflict hit: {})", mixinClassName, describe(condition));
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            LOG.warn("[CCA-Mixin] SKIP {} (check error: {})", mixinClassName, t.toString());
            return false;
        }
    }

    private static AnnotationNode readRestriction(String mixinClassName) throws Exception {
        ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName);
        return Annotations.getVisible(node, Restriction.class);
    }

    /**
     * 注解里没写的成员不会进字节码，{@code getValue} 会拿到 null，这里统一折成空表。
     * 用原始 {@code getValue(node, key)} 取 List，避免依赖不同 Mixin 版本里重载签名差异。
     */
    @SuppressWarnings("unchecked")
    private static List<AnnotationNode> conditionList(AnnotationNode node, String key) {
        Object value = Annotations.getValue(node, key);
        if (value instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof AnnotationNode) {
            return (List<AnnotationNode>) l;
        }
        return Collections.emptyList();
    }

    /** 同 {@link #conditionList}，用于 {@code String[] versionPredicates}。 */
    @SuppressWarnings("unchecked")
    private static List<String> stringList(AnnotationNode node, String key) {
        Object value = Annotations.getValue(node, key);
        if (value instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof String) {
            return (List<String>) l;
        }
        return Collections.emptyList();
    }

    private static boolean test(String mixinClassName, AnnotationNode condition) throws Exception {
        Condition.Type type = Annotations.getValue(condition, "type", Condition.Type.class, Condition.Type.MOD);
        switch (type) {
            case MOD: {
                String modId = Annotations.getValue(condition, "value");
                if (modId == null || modId.isEmpty()) {
                    return false;
                }
                List<String> predicates = stringList(condition, "versionPredicates");
                return CCAUtils.isLoad(modId, predicates.toArray(new String[0]));
            }
            case MIXIN: {
                String other = Annotations.getValue(condition, "value");
                if (other == null || other.isEmpty() || other.equals(mixinClassName)) {
                    return false;
                }
                return shouldApply(mixinClassName, other);
            }
            case TESTER: {
                Type testerType = Annotations.getValue(condition, "tester");
                if (testerType == null) {
                    return false;
                }
                Class<?> clazz = Class.forName(testerType.getClassName(), true,
                        MixinRestrictionProcessor.class.getClassLoader());
                if (clazz.isInterface()) {
                    LOG.error("[CCA-Mixin] tester {} is an interface, skip", clazz.getName());
                    return false;
                }
                Object tester = clazz.getConstructor().newInstance();
                return ((ConditionTester) tester).isSatisfied(mixinClassName);
            }
            default:
                return false;
        }
    }

    private static String describe(AnnotationNode condition) {
        if (condition == null) {
            return "null";
        }
        try {
            Condition.Type type = Annotations.getValue(condition, "type", Condition.Type.class, Condition.Type.MOD);
            if (type == Condition.Type.TESTER) {
                Type tester = Annotations.getValue(condition, "tester");
                return "TESTER:" + (tester == null ? "?" : tester.getClassName());
            }
            String value = Annotations.getValue(condition, "value");
            List<String> predicates = stringList(condition, "versionPredicates");
            return type + ":" + value + (predicates.isEmpty() ? "" : "@" + predicates);
        } catch (Throwable t) {
            return "unreadable(" + t + ")";
        }
    }
}
