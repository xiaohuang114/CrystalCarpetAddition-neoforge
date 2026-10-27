package crystal0404.crystalcarpetaddition;

import carpet.api.settings.Rule;
import crystal0404.crystalcarpetaddition.utils.annotation.Condition;
import crystal0404.crystalcarpetaddition.utils.annotation.Restriction;
import crystal0404.crystalcarpetaddition.utils.shulkerBoxUtils.ColourMap;

public class CCASettings {
    /** Public alias of {@link #CCA}, added in s2 so other classes can count CCA-category rules. */
    public static final String CCA_CATEGORY = "CCA";
    private static final String CCA = "CCA";
    private static final String CREATIVE = "Creative";
    private static final String MC_TWEAKS = "MCTweaks";

    @Rule(categories = {"CCA", "MCTweaks"})
    public static boolean AnvilCanCrushItemEntities = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    public static boolean ComparatorIgnoresStateUpdatesFromBelow = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.21"))
    public static boolean EnderDragonPartCanUseEndPortal = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    public static boolean EndermanCannotPickUpBlocksInNether = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.21"))
    public static boolean GatewayCannotLoadingChunks = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(conflict = {
            @Condition("antishadowpatch"),
            @Condition("carpet-fixes"),
            @Condition("carpet-shadow")
    })
    public static boolean ItemShadowing = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    public static boolean MagicBox = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    public static boolean NoBatSpawn = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.20.5"))
    public static boolean ReIntroduceOldVersionRaid = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.21"))
    public static boolean ReIntroduceOldVersionWitchLootTable = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(require = @Condition(value = "minecraft", versionPredicates = ">=1.21.4"))
    public static boolean RemoveVillagerTradeDistanceLimit = false;

    @Rule(categories = {"CCA", "MCTweaks"})
    @Restriction(conflict = @Condition("pca"))
    public static boolean StackableShulkerBoxesEnhancement = false;

    @Rule(categories = {"CCA", "Creative"})
    public static boolean ShulkerBoxPowerOutputExpansion = false;

    @Rule(categories = {"CCA", "Creative"})
    public static ColourMap.Colour ShulkerBoxPowerOutputExpansionColour = ColourMap.Colour.PINK;
}
