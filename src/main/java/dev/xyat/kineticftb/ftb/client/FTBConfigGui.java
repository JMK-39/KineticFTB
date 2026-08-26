package dev.xyat.kineticftb.ftb.client;

import dev.xyat.kineticcore.config.client.KTConfigApi;
import dev.xyat.kineticcore.config.client.KTConfigPage;
import dev.xyat.kineticcore.config.client.KTConfigScope;
import dev.xyat.kineticftb.ftb.client.gui.FTBItemBindingEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** FTB Quests contributes this page only while that optional mod is installed. */
public final class FTBConfigGui {
    public static final String PAGE_ID = "kineticftb:ftb_quests";

    private FTBConfigGui() {
    }

    public static void load() {
        KTConfigApi.register(KTConfigPage.builder(
                        PAGE_ID,
                        Component.translatable("cfg.kineticftb.ftb.title")
                )
                .scope(KTConfigScope.CLIENT_LOCAL)
                .applyTiming(KTConfigPage.ApplyTiming.IMMEDIATE)
                .booleanValue(
                        "enable_task_jump",
                        Component.translatable("cfg.kineticftb.ftb.enable_task_jump"),
                        FTBClientConfig::isTaskJumpEnabled,
                        FTBClientConfig::setTaskJumpEnabled,
                        true,
                        Component.translatable("cfg.kineticftb.ftb.enable_task_jump.tooltip")
                )
                .action(
                        "open_binding_editor",
                        Component.translatable("cfg.kineticftb.ftb.open_editor"),
                        FTBConfigGui::openEditor,
                        Component.translatable("cfg.kineticftb.ftb.open_editor.tooltip")
                )
                .onSave(FTBClientConfig::save)
                .build());
    }

    public static Screen create(Screen parent) {
        return KTConfigApi.createScreen(parent, PAGE_ID);
    }

    private static void openEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new FTBItemBindingEditorScreen(minecraft.screen));
    }
}
