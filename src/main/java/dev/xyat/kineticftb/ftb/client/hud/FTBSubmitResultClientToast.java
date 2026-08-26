package dev.xyat.kineticftb.ftb.client.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class FTBSubmitResultClientToast {
    private FTBSubmitResultClientToast() {
    }

    public static void show(int requestedTimes, int completedTimes, long submittedItems) {
        int requested = Math.max(1, requestedTimes);
        int completed = Math.max(0, completedTimes);
        long submitted = Math.max(0L, submittedItems);

        Component message;
        if (completed == 0 && submitted > 0L) {
            message = Component.translatable("toast.kineticftb.ftb.submit.partial_items", number(submitted, ChatFormatting.GOLD));
        } else if (completed == 0) {
            message = Component.translatable("toast.kineticftb.ftb.submit.none", number(requested, ChatFormatting.AQUA));
        } else if (completed < requested) {
            message = Component.translatable("toast.kineticftb.ftb.submit.partial", number(requested, ChatFormatting.AQUA), number(completed, ChatFormatting.GREEN));
        } else {
            message = Component.translatable("toast.kineticftb.ftb.submit.success", number(completed, ChatFormatting.AQUA));
        }

        FTBToastUtil.showLong("kineticftb.ftb.submit.result", message);
    }

    private static Component number(long value, ChatFormatting color) {
        return Component.literal(String.valueOf(value)).withStyle(color);
    }
}
