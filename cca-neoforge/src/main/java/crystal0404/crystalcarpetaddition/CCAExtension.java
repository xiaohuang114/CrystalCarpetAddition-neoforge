package crystal0404.crystalcarpetaddition;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import carpet.utils.Translations;
import java.util.Map;

public class CCAExtension implements CarpetExtension {
    public static final SettingsManager CCASettingsManager =
            new SettingsManager(CrystalCarpetAdditionMod.version, "cca", "Crystal Carpet Addition");
    private static final CarpetExtension INSTANCE = new CCAExtension();

    public static void init() {
        CarpetServer.manageExtension(INSTANCE);
    }

    @Override
    public void onGameStarted() {
        CCASettingsManager.parseSettingsClass(CCASettings.class);
        CCASettingsManager.getCarpetRules().forEach(rule ->
                CrystalCarpetAdditionMod.LOGGER.info("[CCA] rule {}", rule.name()));
        CrystalCarpetAdditionMod.LOGGER.info("[CCA] registered {} rules", CCASettingsManager.getCarpetRules().size());
        CrystalCarpetAdditionMod.LOGGER.info("[CCA] version={} CCA-category rules={}",
                CrystalCarpetAdditionMod.version,
                CCASettingsManager.getCarpetRules().stream()
                        .filter(r -> r.categories().contains(CCASettings.CCA_CATEGORY)).count());
    }

    @Override
    public String version() {
        return CrystalCarpetAdditionMod.version;
    }

    @Override
    public SettingsManager extensionSettingsManager() {
        return CCASettingsManager;
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return Translations.getTranslationFromResourcePath("assets/cca/lang/%s.json".formatted(lang));
    }
}
