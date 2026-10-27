package crystal0404.crystalcarpetaddition.utils;

import crystal0404.crystalcarpetaddition.utils.condition.MixinRestrictionProcessor;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 替代 me.fallenbreath.conditionalmixin 的 RestrictiveMixinConfigPlugin。
 * <p>
 * Mixin 0.8.7 的 {@link IMixinConfigPlugin} 恰好 7 个抽象方法（javap 确认，无 onSelect），全部实现。
 */
public class CCAMixinConfigPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        // NeoForge 21.1 已自行初始化 MixinExtras（META-INF/jarjar/mixinextras-neoforge-0.5.5.jar），
        // 这里不许调用 MixinExtrasBootstrap.init()。
    }

    @Override
    public String getRefMapperConfig() {
        return null; // NeoForge 1.21.1 走 official 名，无 refmap
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return MixinRestrictionProcessor.shouldApply(targetClassName, mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
