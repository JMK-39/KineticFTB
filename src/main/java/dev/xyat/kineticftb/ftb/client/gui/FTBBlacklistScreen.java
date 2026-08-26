package dev.xyat.kineticftb.ftb.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ItemSelectorScreen;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.ScrollUtil;
import dev.xyat.kineticftb.ftb.client.hud.FTBToastUtil;
import dev.xyat.kineticftb.ftb.data.BindingStoreFTB;
import dev.xyat.kineticftb.ftb.data.BlacklistStoreFTB;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FTBBlacklistScreen extends ScaledScreen {
    private static final int SLOT_SIZE = 20;
    private final Screen parent;
    private final List<String> allEntries = new ArrayList<>();

    private int gridX, gridY, gridCols, gridRowsVisible;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean isScrolling = false;

    public FTBBlacklistScreen(Screen parent) {
        super(Component.translatable("screen.kineticftb.ftb.blacklist"));
        this.parent = parent;
        configureResponsiveCanvas(
                500f,
                320f,
                6
        );
        this.minScale = 0.5f;
    }

    @Override
    protected void initScaled() {
        reloadEntries();
        int paddingX = 14;
        int maxAvailableWidth = this.vWidth - paddingX * 2 - 8 - 4;
        this.gridCols = Math.max(1, maxAvailableWidth / SLOT_SIZE);
        int contentW = gridCols * SLOT_SIZE;
        this.gridX = (this.vWidth - (contentW + 8 + 4)) / 2;
        this.gridY = 36;
        int bottomY = this.vHeight - 10;
        this.gridRowsVisible = Math.max(1, (bottomY - gridY) / SLOT_SIZE);

        int rightEdge = gridX + contentW + 8 + 4;

        addRenderableWidget(Button.builder(Component.translatable("button.kineticftb.ftb.blacklist.add"), b -> openSelector())
                .bounds(gridX, 10, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(rightEdge - 60, 10, 60, 20).build());
    }

    private void reloadEntries() {
        allEntries.clear();
        allEntries.addAll(BlacklistStoreFTB.getAll());
        maxScroll = Math.max(0, (int) Math.ceil((double) allEntries.size() / gridCols) - gridRowsVisible);
    }

    private void openSelector() {
        Minecraft.getInstance().setScreen(new ItemSelectorScreen(this, selection -> {
            String target = "";
            if (selection.isMod()) target = "@" + selection.value();
            else if (selection.isTag()) target = "#" + selection.value();
            else if (selection.isItem()) {
                ItemStack s = selection.stack();
                target = BindingStoreFTB.itemKey(s);
                if (s.getTag() != null && s.hasTag() && !s.getTag().isEmpty()) {
                    String nbtStr = BindingStoreFTB.stackNbtString(s);
                    if (!nbtStr.isEmpty()) target += "|nbt:" + nbtStr;
                }
            }

            if (!target.isEmpty()) {
                BlacklistStoreFTB.add(target);
                reloadEntries();
                FTBToastUtil.show("kineticftb_blacklist_added", Component.translatable("msg.kineticftb.ftb.blacklist.added"));
            }
        }));
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiRenderUtil.drawShadowOverlay(g, vWidth, vHeight);
        g.fillGradient(0, 0, this.vWidth, this.vHeight, 0xFF222222, 0xFF111111);

        g.drawString(font, Component.translatable("label.kineticftb.ftb.blacklist.count", Component.literal(String.valueOf(allEntries.size())).withStyle(ChatFormatting.GREEN)), gridX + 110, 16, 0xFFFFFFFF, false);

        int contentW = gridCols * SLOT_SIZE;
        int contentH = gridRowsVisible * SLOT_SIZE;
        g.fill(gridX - 3, gridY - 3, gridX + contentW + 8 + 7, gridY + contentH + 3, 0xFF000000);
        g.fill(gridX - 2, gridY - 2, gridX + contentW + 8 + 6, gridY + contentH + 2, 0xFF2A2A2A);
    }

    @Override
    protected void renderScaledForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        int startIdx = scrollOffset * gridCols;
        int endIdx = Math.min(startIdx + gridRowsVisible * gridCols, allEntries.size());

        for (int i = startIdx; i < endIdx; i++) {
            String entry = allEntries.get(i);
            int col = (i - startIdx) % gridCols;
            int row = (i - startIdx) / gridCols;
            int x = gridX + col * SLOT_SIZE;
            int y = gridY + row * SLOT_SIZE;

            boolean hovered = mx >= x && mx < x + SLOT_SIZE && my >= y && my < y + SLOT_SIZE;
            ItemStack icon = getIconForRule(entry);
            AdaptiveItemGridRenderer.drawSlot(g, icon, x, y, SLOT_SIZE, 4, hovered);

            RenderSystem.enableDepthTest();
            g.renderItem(icon, x + 2, y + 2);
            g.renderItemDecorations(this.font, icon, x + 2, y + 2);
            RenderSystem.disableDepthTest();

            if (entry.startsWith("@") || entry.startsWith("#")) {
                g.pose().pushPose();
                g.pose().translate(x + 2, y + 10, 200);
                g.pose().scale(0.6f, 0.6f, 1.0f);
                g.drawString(font, entry.startsWith("@") ? "@Mod" : "#Tag", 0, 0, 0xFFFFAA00, true);
                g.pose().popPose();
            } else if (entry.contains("|nbt:")) {
                g.pose().pushPose();
                g.pose().translate(x + 2, y + 10, 200);
                g.pose().scale(0.6f, 0.6f, 1.0f);
                g.drawString(font, "NBT", 0, 0, 0xFF55FFFF, true);
                g.pose().popPose();
            }

            g.fill(x + 1, y + SLOT_SIZE - 2, x + SLOT_SIZE - 1, y + SLOT_SIZE, 0xFFFF3333);

        }

        if (maxScroll > 0) {
            int contentH = gridRowsVisible * SLOT_SIZE;
            int thumbH = ScrollUtil.calculateThumbHeight(contentH, gridRowsVisible, (int) Math.ceil((double) allEntries.size() / gridCols), 20);
            ScrollUtil.renderScrollbar(g, gridX + gridCols * SLOT_SIZE + 4, gridY, 8, contentH, thumbH, maxScroll, scrollOffset, isScrolling);
        }
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        int startIdx = scrollOffset * gridCols;
        int endIdx = Math.min(startIdx + gridRowsVisible * gridCols, allEntries.size());
        for (int i = startIdx; i < endIdx; i++) {
            int x = gridX + (i - startIdx) % gridCols * SLOT_SIZE;
            int y = gridY + (i - startIdx) / gridCols * SLOT_SIZE;
            if (smx >= x && smx < x + SLOT_SIZE && smy >= y && smy < y + SLOT_SIZE) {
                String entry = allEntries.get(i);
                ItemStack icon = getIconForRule(entry);
                List<Component> tips = new ArrayList<>();

                if (entry.startsWith("@")) {
                    tips.add(Component.translatable("label.kineticftb.ftb.type.mod"));
                } else if (entry.startsWith("#")) {
                    tips.add(Component.translatable("label.kineticftb.ftb.type.tag"));
                } else {
                    tips.add(icon.getHoverName());
                }

                tips.add(Component.literal(entry).withStyle(net.minecraft.ChatFormatting.GOLD));
                tips.add(Component.translatable("tip.kineticftb.ftb.blacklist.remove"));

                g.renderComponentTooltip(font, tips, mx, my);
                return;
            }
        }
    }

    @Override
    protected boolean universalMouseClicked(double mx, double my, int btn) {
        int contentW = gridCols * SLOT_SIZE;
        int contentH = gridRowsVisible * SLOT_SIZE;

        if (maxScroll > 0 && mx >= gridX + contentW + 4 && mx <= gridX + contentW + 4 + 8 && my >= gridY && my < gridY + contentH) {
            this.isScrolling = true;
            return true;
        }

        if (mx >= gridX && mx < gridX + contentW && my >= gridY && my < gridY + contentH) {
            int idx = (scrollOffset + (int) ((my - gridY) / SLOT_SIZE)) * gridCols + (int) ((mx - gridX) / SLOT_SIZE);
            if (idx >= 0 && idx < allEntries.size()) {
                if (btn == 1) { // 仅右键触发移除
                    BlacklistStoreFTB.remove(allEntries.get(idx));
                    reloadEntries();
                    FTBToastUtil.show("kineticftb_blacklist_removed", Component.translatable("msg.kineticftb.ftb.blacklist.removed"));
                }
            }
            return true;
        }
        return super.universalMouseClicked(mx, my, btn);
    }

    @Override
    protected boolean universalMouseReleased(double mx, double my, int btn) {
        if (btn == 0) this.isScrolling = false;
        return super.universalMouseReleased(mx, my, btn);
    }

    @Override
    protected boolean universalMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (this.isScrolling && maxScroll > 0) {
            int contentH = gridRowsVisible * SLOT_SIZE;
            int thumbH = ScrollUtil.calculateThumbHeight(contentH, gridRowsVisible, (int) Math.ceil((double) allEntries.size() / gridCols), 20);
            this.scrollOffset = ScrollUtil.calculateScrollOffset(my, gridY, contentH, thumbH, maxScroll);
            return true;
        }
        return super.universalMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean universalMouseScrolled(double mx, double my, double d) {
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) d, maxScroll));
            return true;
        }
        return false;
    }

    private ItemStack getIconForRule(String rule) {
        if (rule.startsWith("@")) {
            String modid = rule.substring(1);
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
                if (rl != null && rl.getNamespace().equals(modid)) return new ItemStack(item);
            }
            return new ItemStack(Items.BARRIER);
        } else if (rule.startsWith("#")) {
            return BindingStoreFTB.createDisplayStack(rule, "");
        } else {
            String[] parts = rule.split("\\|nbt:", 2);
            return BindingStoreFTB.createDisplayStack(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
