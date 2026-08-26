package dev.xyat.kineticftb.ftb.util;

import dev.xyat.kineticftb.ftb.mixin.client.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

public final class HoveredItemFTB {
    private static final long TOOLTIP_CACHE_MS = 1000L;
    private static final double TOOLTIP_MOUSE_TOLERANCE = 6.0D;

    private static ItemStack lastTooltipStack = ItemStack.EMPTY;
    private static Screen lastTooltipScreen;
    private static long lastTooltipTime;
    private static double lastTooltipMouseX;
    private static double lastTooltipMouseY;

    private HoveredItemFTB() {
    }

    public static void rememberTooltipStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            return;
        }

        lastTooltipStack = stack.copy();
        lastTooltipScreen = mc.screen;
        lastTooltipTime = System.currentTimeMillis();
        lastTooltipMouseX = scaledMouseX(mc);
        lastTooltipMouseY = scaledMouseY(mc);
    }

    public static ItemStack getHoveredStack(Screen screen) {
        ItemStack jeiStack = getJeiHoveredStack();
        if (!jeiStack.isEmpty()) {
            return jeiStack;
        }

        ItemStack containerStack = getContainerHoveredStack(screen);
        if (!containerStack.isEmpty()) {
            return containerStack;
        }

        ItemStack tooltipStack = getRecentTooltipStack(screen);
        if (!tooltipStack.isEmpty()) {
            return tooltipStack;
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getJeiHoveredStack() {
        if (!ModList.get().isLoaded("jei")) {
            return ItemStack.EMPTY;
        }

        try {
            return JeiHoveredItemFTB.getHoveredItemStack();
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack getRecentTooltipStack(Screen screen) {
        if (lastTooltipStack.isEmpty() || screen == null || screen != lastTooltipScreen) {
            return ItemStack.EMPTY;
        }

        long age = System.currentTimeMillis() - lastTooltipTime;
        if (age < 0L || age > TOOLTIP_CACHE_MS) {
            return ItemStack.EMPTY;
        }

        Minecraft mc = Minecraft.getInstance();
        double mouseX = scaledMouseX(mc);
        double mouseY = scaledMouseY(mc);

        if (Math.abs(mouseX - lastTooltipMouseX) > TOOLTIP_MOUSE_TOLERANCE || Math.abs(mouseY - lastTooltipMouseY) > TOOLTIP_MOUSE_TOLERANCE) {
            return ItemStack.EMPTY;
        }

        return lastTooltipStack.copy();
    }

    private static ItemStack getContainerHoveredStack(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return ItemStack.EMPTY;
        }

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;

        Slot hoveredSlot = accessor.kineticftb$getHoveredSlot();
        if (isValidSlot(hoveredSlot)) {
            return hoveredSlot.getItem().copy();
        }

        Slot mouseSlot = findSlotByMouse(containerScreen, accessor);
        if (isValidSlot(mouseSlot)) {
            return mouseSlot.getItem().copy();
        }

        ItemStack carried = containerScreen.getMenu().getCarried();
        if (!carried.isEmpty()) {
            return carried.copy();
        }

        return ItemStack.EMPTY;
    }

    private static Slot findSlotByMouse(AbstractContainerScreen<?> screen, AbstractContainerScreenAccessor accessor) {
        Minecraft mc = Minecraft.getInstance();

        double mouseX = scaledMouseX(mc);
        double mouseY = scaledMouseY(mc);

        int left = accessor.kineticftb$getLeftPos();
        int top = accessor.kineticftb$getTopPos();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;

            int slotX = left + slot.x;
            int slotY = top + slot.y;

            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot;
            }
        }

        return null;
    }

    private static double scaledMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
    }

    private static double scaledMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
    }

    private static boolean isValidSlot(Slot slot) {
        return slot != null && slot.isActive() && slot.hasItem() && !slot.getItem().isEmpty();
    }
}
