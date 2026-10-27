package crystal0404.crystalcarpetaddition.utils.shulkerBoxUtils;

import com.google.common.collect.ImmutableMap;
import crystal0404.crystalcarpetaddition.CCASettings;
import net.minecraft.world.item.DyeColor;

public class ColourMap {
    private static final ImmutableMap<ColourMap.Colour, DyeColor> DYE_COLOR_IMMUTABLE_MAP =
            ImmutableMap.<ColourMap.Colour, DyeColor>builder()
                    .put(ColourMap.Colour.PINK, DyeColor.PINK)
                    .put(ColourMap.Colour.WHITE, DyeColor.WHITE)
                    .put(ColourMap.Colour.LIGHT_GRAY, DyeColor.LIGHT_GRAY)
                    .put(ColourMap.Colour.GRAY, DyeColor.GRAY)
                    .put(ColourMap.Colour.BLACK, DyeColor.BLACK)
                    .put(ColourMap.Colour.BROWN, DyeColor.BROWN)
                    .put(ColourMap.Colour.RED, DyeColor.RED)
                    .put(ColourMap.Colour.ORANGE, DyeColor.ORANGE)
                    .put(ColourMap.Colour.YELLOW, DyeColor.YELLOW)
                    .put(ColourMap.Colour.LIME, DyeColor.LIME)
                    .put(ColourMap.Colour.GREEN, DyeColor.GREEN)
                    .put(ColourMap.Colour.CYAN, DyeColor.CYAN)
                    .put(ColourMap.Colour.LIGHT_BLUE, DyeColor.LIGHT_BLUE)
                    .put(ColourMap.Colour.BLUE, DyeColor.BLUE)
                    .put(ColourMap.Colour.PURPLE, DyeColor.PURPLE)
                    .put(ColourMap.Colour.MAGENTA, DyeColor.MAGENTA)
                    .buildOrThrow();

    public static DyeColor getSettingColour() {
        return CCASettings.ShulkerBoxPowerOutputExpansionColour == ColourMap.Colour.NONE
                ? null
                : DYE_COLOR_IMMUTABLE_MAP.get(CCASettings.ShulkerBoxPowerOutputExpansionColour);
    }

    public enum Colour {
        NONE,
        WHITE,
        LIGHT_GRAY,
        GRAY,
        BLACK,
        BROWN,
        RED,
        ORANGE,
        YELLOW,
        LIME,
        GREEN,
        CYAN,
        LIGHT_BLUE,
        BLUE,
        PURPLE,
        MAGENTA,
        PINK
    }
}
