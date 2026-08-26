package dev.xyat.kineticftb.ftb.client;

import dev.xyat.kineticftb.KineticFTB;
import dev.xyat.kineticftb.ftb.data.BlacklistStoreFTB;
import dev.xyat.kineticftb.ftb.data.BindingStoreFTB;
import dev.xyat.kineticftb.ftb.data.FavoritesStoreFTB;
import dev.xyat.kineticftb.ftb.event.ClientEventsFTB;
import dev.xyat.kineticftb.ftb.util.BridgeFTB;
import dev.xyat.kineticftb.ftb.util.QuestMatchCacheFTB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ItemClientModuleFTB {
    private static boolean enabled = false;

    private ItemClientModuleFTB() {
    }

    public static void register(IEventBus modEventBus) {
        FTBClientConfig.register();


        enabled = true;
        BridgeFTB.init();
        BindingStoreFTB.load();
        FavoritesStoreFTB.load();
        BlacklistStoreFTB.load();
        QuestMatchCacheFTB.rebuild();

        modEventBus.addListener(KeyMappingsFTB::register);
        MinecraftForge.EVENT_BUS.register(ClientEventsFTB.class);
    }

    public static boolean isEnabled() {
        return enabled;
    }


}