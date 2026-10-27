package crystal0404.crystalcarpetaddition;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(CrystalCarpetAdditionMod.MOD_ID)
public class CrystalCarpetAdditionMod {
    public static final String MOD_ID = "cca";
    public static final String MOD_NAME = "Crystal Carpet Addition";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Assigned by the @Mod constructor before CCAExtension is first touched. */
    public static String version = "1.11.1";

    public CrystalCarpetAdditionMod(IEventBus modBus, ModContainer container) {
        try {
            String v = container.getModInfo().getVersion().toString();
            if (v != null && !v.isBlank()) {
                version = v;
            }
        } catch (Throwable t) {
            LOGGER.warn("[CCA] failed to read mod version, fallback to {}", version, t);
        }
        CCAExtension.init();
    }
}
