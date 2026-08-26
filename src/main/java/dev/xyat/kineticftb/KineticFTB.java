package dev.xyat.kineticftb;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticftb.ftb.client.FTBClientConfig;
import dev.xyat.kineticftb.ftb.client.FTBConfigGui;
import dev.xyat.kineticftb.ftb.client.ItemClientModuleFTB;
import dev.xyat.kineticftb.ftb.network.FTBSubmitLimitNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(KineticFTB.MODID)
public final class KineticFTB {
    public static final String MODID = "kineticftb";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticFTB(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        FTBSubmitLimitNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            FTBClientConfig.register();
            ItemClientModuleFTB.register(modEventBus);
            FTBConfigGui.load();
        });
    }
}
