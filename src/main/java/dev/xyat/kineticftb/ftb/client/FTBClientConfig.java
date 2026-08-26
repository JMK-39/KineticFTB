package dev.xyat.kineticftb.ftb.client;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class FTBClientConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLE_TASK_JUMP;
    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("ftb_item_task_jump");
        ENABLE_TASK_JUMP = builder
                .comment(
                        "是否启用 FTB 任务物品跳转提示与跳转按键。",
                        "Enable FTB quest item jump tooltip and jump hotkey."
                )
                .define("enableTaskJump", true);
        builder.pop();

        SPEC = builder.build();
    }

    private FTBClientConfig() {
    }

    public static void register() {
        if (registered) return;
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "kineticcore/ftb_item_client.toml");
        registered = true;
    }

    public static boolean shouldSkipTaskJump() {
        return !ENABLE_TASK_JUMP.get();
    }

    public static boolean isTaskJumpEnabled() {
        return ENABLE_TASK_JUMP.get();
    }

    public static void setTaskJumpEnabled(boolean enabled) {
        ENABLE_TASK_JUMP.set(enabled);
    }

    public static void save() {
        SPEC.save();
    }

}
