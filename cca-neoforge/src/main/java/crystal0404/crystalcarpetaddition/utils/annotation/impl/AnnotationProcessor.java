package crystal0404.crystalcarpetaddition.utils.annotation.impl;

import com.mojang.logging.LogUtils;
import crystal0404.crystalcarpetaddition.utils.CCAUtils;
import crystal0404.crystalcarpetaddition.utils.annotation.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.Restriction;
import org.slf4j.Logger;

public abstract class AnnotationProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean shouldRegister(Restriction restriction, String rule) {
        for (Condition condition : restriction.conflict()) {
            if (CCAUtils.isLoad(condition.value(), condition.versionPredicates())) {
                conflictInfo(rule, condition);
                return false;
            }
        }
        for (Condition condition : restriction.require()) {
            if (!CCAUtils.isLoad(condition.value(), condition.versionPredicates())) {
                requireInfo(rule, condition);
                return false;
            }
        }
        return true;
    }

    private static void conflictInfo(String rule, Condition condition) {
        if (CCAUtils.isEnableDebug()) {
            LOGGER.info("[CCA] Rule {} is disabled, Because of the conflict with {}({}).",
                    rule, condition.value(), condition.versionPredicates());
        }
    }

    private static void requireInfo(String rule, Condition condition) {
        if (CCAUtils.isEnableDebug()) {
            LOGGER.info("[CCA] Rule {} is disabled, Because of the require with {}({}).",
                    rule, condition.value(), condition.versionPredicates());
        }
    }
}
