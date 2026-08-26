package dev.xyat.kineticftb.ftb.client.gui;

import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.xyat.kineticftb.ftb.api.FTBTaskSubmitHelper;
import dev.xyat.kineticftb.ftb.client.FTBVirtualItemClientState;
import dev.xyat.kineticftb.ftb.network.FTBSubmitLimitNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class FTBSubmitCountScreen extends Screen {
    private static final int PANEL_W = 420;
    private static final int PANEL_H = 230;
    private static final int MARGIN_X = 24;
    private static final int BUTTON_W = 150;
    private static final int BUTTON_H = 22;
    private static final long STATS_REFRESH_MS = 250L;

    private final Screen parent;
    private final ItemTask task;
    private EditBox countBox;
    private int left;
    private int top;
    private String lastCountValue = "";
    private long lastStatsRefreshMs = 0L;
    private int cachedCount = 1;
    private long cachedPerExchange = 1L;
    private long cachedRequiredItems = 1L;
    private long cachedInventoryItems = 0L;
    private long cachedVirtualItems = 0L;
    private boolean cachedVirtualSupported = false;
    private int cachedInventoryEstimatedTimes = 0;
    private int cachedTotalEstimatedTimes = 0;

    public FTBSubmitCountScreen(Screen parent, ItemTask task) {
        super(Component.translatable("screen.kineticftb.ftb.submit"));
        this.parent = parent;
        this.task = task;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        countBox = new EditBox(font, left + MARGIN_X, top + 52, PANEL_W - MARGIN_X * 2, 22, Component.translatable("placeholder.kineticftb.ftb.submit.count"));
        countBox.setValue("1");
        addRenderableWidget(countBox);

        int buttonY = top + PANEL_H - 38;
        addRenderableWidget(Button.builder(Component.translatable("button.kineticftb.ftb.submit.confirm"), button -> submit())
                .bounds(left + MARGIN_X, buttonY, BUTTON_W, BUTTON_H)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + PANEL_W - MARGIN_X - BUTTON_W, buttonY, BUTTON_W, BUTTON_H)
                .build());

        refreshStats(true);
        setInitialFocus(countBox);
    }

    private void submit() {
        if (!FTBTaskSubmitHelper.isCustomSubmitAllowed(task)) {
            FTBSubmitLimitNetwork.sendSubmit(task.id, 1);
            Minecraft.getInstance().setScreen(parent);
            return;
        }

        refreshStats(true);
        FTBSubmitLimitNetwork.sendSubmit(task.id, cachedCount);
        Minecraft.getInstance().setScreen(parent);
    }

    private int parseCountValue(String value) {
        try {
            return Math.max(1, Math.min(1_000_000, Integer.parseInt(value.trim())));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void refreshStats(boolean force) {
        String value = countBox == null ? "" : countBox.getValue().trim();
        long now = System.currentTimeMillis();
        if (!force && value.equals(lastCountValue) && now - lastStatsRefreshMs < STATS_REFRESH_MS) {
            return;
        }

        lastCountValue = value;
        lastStatsRefreshMs = now;
        cachedCount = parseCountValue(value);
        cachedPerExchange = Math.max(1L, task.getMaxProgress());
        cachedRequiredItems = safeMultiply(cachedPerExchange, cachedCount);
        cachedInventoryItems = calculateInventoryItems(cachedRequiredItems);
        FTBVirtualItemClientState.Snapshot virtualItems = FTBVirtualItemClientState.request(task.id);
        cachedVirtualSupported = virtualItems.supported();
        cachedVirtualItems = virtualItems.count();
        cachedInventoryEstimatedTimes = estimateTimes(cachedInventoryItems, cachedPerExchange, cachedCount);
        cachedTotalEstimatedTimes = estimateTimes(safeAdd(cachedInventoryItems, cachedVirtualItems), cachedPerExchange, cachedCount);
    }

    private long calculateInventoryItems(long stopAt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0L;

        long total = 0L;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.isEmpty()) continue;
            try {
                if (task.test(stack)) {
                    total += stack.getCount();
                    if (total >= stopAt) return total;
                }
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    private int estimateTimes(long amount, long per, int maxTimes) {
        long times = amount / Math.max(1L, per);
        return (int) Math.max(0L, Math.min(maxTimes, times));
    }

    private long safeMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshStats(false);
        renderBackground(graphics);

        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE101010);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + PANEL_H - 1, 0xEE202020);
        graphics.renderOutline(left, top, PANEL_W, PANEL_H, 0xFFFFAA00);

        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 14, 0xFFFFAA00);
        graphics.drawString(font, Component.translatable("label.kineticftb.ftb.submit.desc"), left + MARGIN_X, top + 34, 0xFF55FFFF, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (countBox != null && countBox.getValue().isEmpty() && !countBox.isFocused()) {
            graphics.drawString(font, Component.translatable("placeholder.kineticftb.ftb.submit.count"), countBox.getX() + 5, countBox.getY() + 7, 0xFFAAAAAA, false);
        }

        int lineX = left + MARGIN_X;
        int y = top + 86;
        int gap = 18;

        drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.exchanges", number(cachedCount, ChatFormatting.GREEN)), lineX, y, 0xFFFFFF55);
        y += gap;
        drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.required", number(cachedRequiredItems, ChatFormatting.AQUA)), lineX, y, 0xFF55FF55);
        y += gap;
        drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.inventory", number(cachedInventoryItems, ChatFormatting.YELLOW)), lineX, y, 0xFF55FFFF);
        y += gap;
        drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.inventory.times", number(cachedInventoryEstimatedTimes, ChatFormatting.GREEN)), lineX, y, 0xFFFFAA00);
        y += gap;

        if (cachedVirtualSupported) {
            drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.virtual", number(cachedVirtualItems, ChatFormatting.LIGHT_PURPLE)), lineX, y, 0xFFFF55FF);
            y += gap;
            drawLine(graphics, Component.translatable("label.kineticftb.ftb.submit.total", number(cachedTotalEstimatedTimes, ChatFormatting.GREEN)), lineX, y, 0xFF55FF55);
        }
    }

    private static Component number(long value, ChatFormatting color) {
        return Component.literal(String.valueOf(value)).withStyle(color);
    }

    private void drawLine(GuiGraphics graphics, Component component, int x, int y, int color) {
        graphics.drawString(font, component, x, y, color, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
