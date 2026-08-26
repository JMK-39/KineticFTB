package dev.xyat.kineticftb.ftb.client.gui;

import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ItemBlacklistScreenFTB extends ScaledScreen {
    private static final int PANEL_X = 10;
    private static final int PANEL_Y = 10;
    private static final int PANEL_W = 600;
    private static final int PANEL_H = 400;
    private static final int LIST_X = 24;
    private static final int LIST_Y = 88;
    private static final int LIST_W = 572;
    private static final int LIST_H = 300;
    private static final int ROW_H = 28;

    private final Screen parent;
    private final List<String> allEntries = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();

    private EditBox searchBox;
    private int scroll;
    private boolean draggingScrollbar;

    public ItemBlacklistScreenFTB(Screen parent) {
        super(Component.translatable("screen.kineticftb.ftb_item.blacklist"));
        this.parent = parent;
        configureResponsiveCanvas(
                620f,
                420f,
                6
        );
        this.minScale = 0.5f;
    }

    @Override
    protected void initScaled() {
        reloadEntries();

        searchBox = new EditBox(this.font, LIST_X + 110, 54, 350, 20, Component.translatable("gui.kineticftb.search"));
        searchBox.setResponder(s -> {
            applySearch();
            scroll = 0;
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.translatable("button.kineticftb.ftb_item.add_blacklist"), b -> Minecraft.getInstance().setScreen(new ItemSelectorScreen(this, selection -> {
            String target = "";
            if (selection.isMod()) target = "@" + selection.value();
            else if (selection.isTag()) target = "#" + selection.value();
            else if (selection.isItem()) target = BindingStoreFTB.itemKey(selection.stack());

            if (!target.isEmpty()) {
                BlacklistStoreFTB.add(target);
                reloadEntries();
                FTBToastUtil.show("kineticftb_blacklist_added", Component.translatable("msg.kineticftb.ftb_item.blacklist_added"));
            }
        }))).bounds(LIST_X, 52, 100, 22).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(LIST_X + 470, 52, 100, 22)
                .build());
    }

    private void reloadEntries() {
        allEntries.clear();
        allEntries.addAll(BlacklistStoreFTB.getAll());
        applySearch();
    }

    private void applySearch() {
        filtered.clear();
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase();
        for (String entry : allEntries) {
            if (q.isEmpty() || AdvancedSearchUtil.match(entry.toLowerCase(), q)) {
                filtered.add(entry);
            }
        }
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiRenderUtil.drawShadowOverlay(g, vWidth, vHeight);
        GuiRenderUtil.drawStandardPanel(g, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        g.drawString(font, Component.translatable("screen.kineticftb.ftb_item.blacklist"), 24, 24, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("label.kineticftb.ftb_item.blacklist_desc"), 150, 25, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("label.kineticftb.ftb_item.blacklist_count", Component.literal(String.valueOf(filtered.size())).withStyle(ChatFormatting.GREEN)), 480, 25, 0xFFFFFFFF, false);

        renderList(g, mx, my);
    }

    @Override
    protected void renderScaledForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, Component.translatable("placeholder.kineticftb.ftb_item.binding_search"), searchBox.getX() + 6, searchBox.getY() + 6, 0xFF888888, false);
            g.pose().popPose();
        }
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int visible = LIST_H / ROW_H;
        int maxScroll = Math.max(0, filtered.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;

        GuiRenderUtil.drawDarkPanel(g, LIST_X, LIST_Y, LIST_W, LIST_H);

        if (filtered.isEmpty()) {
            g.drawString(font, Component.translatable("label.kineticftb.ftb_item.empty_blacklist"), LIST_X + 10, LIST_Y + 14, 0xFFFFFFFF, false);
        }

        int start = scroll;
        int end = Math.min(filtered.size(), start + visible);
        for (int i = start; i < end; i++) {
            int rowY = LIST_Y + (i - start) * ROW_H;
            String entry = filtered.get(i);

            int drawX = LIST_X + 2;
            int drawY = rowY + 2;
            int drawW = LIST_W - 14;
            int drawH = ROW_H - 2;

            boolean hover = GuiRenderUtil.isHovering(mx, my, drawX, drawY, drawW, drawH);
            int delW = 52;
            int delX = drawX + drawW - delW - 4;
            boolean deleteHover = GuiRenderUtil.isHovering(mx, my, delX, drawY + 2, delW, drawH - 6);

            g.fill(drawX, drawY, drawX + drawW, drawY + drawH, hover ? 0x66336699 : 0x33000000);
            g.renderOutline(drawX, drawY, drawW, drawH, 0xFFFFAA00);

            ItemStack stack = BindingStoreFTB.createDisplayStack(entry, "");
            AdaptiveItemGridRenderer.drawSlot(g, stack, drawX + 5, drawY + 3, 18, 4, false);
            if (!stack.isEmpty()) {
                g.renderItem(stack, drawX + 6, drawY + 4);
            }

            g.drawString(font, entry, drawX + 28, drawY + 8, 0xFFFFFFFF, false);

            g.fill(delX, drawY + 2, delX + delW, drawY + drawH - 2, deleteHover ? 0xAA993333 : 0x88553333);
            Component del = Component.translatable("button.kineticftb.ftb_item.delete");
            g.drawString(font, del, delX + delW / 2 - font.width(del) / 2, drawY + 6, 0xFFFFFFFF, false);
        }

        int thumb = ScrollUtil.calculateThumbHeight(LIST_H, visible, filtered.size(), 24);
        ScrollUtil.renderScrollbar(g, LIST_X + LIST_W - 8, LIST_Y, 6, LIST_H, thumb, maxScroll, scroll, draggingScrollbar);
    }

    @Override
    protected boolean universalMouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int visible = LIST_H / ROW_H;
            int maxScroll = Math.max(0, filtered.size() - visible);

            if (mx >= LIST_X + LIST_W - 10 && mx <= LIST_X + LIST_W && my >= LIST_Y && my <= LIST_Y + LIST_H && maxScroll > 0) {
                int thumb = ScrollUtil.calculateThumbHeight(LIST_H, visible, filtered.size(), 24);
                scroll = ScrollUtil.calculateScrollOffset(my, LIST_Y, LIST_H, thumb, maxScroll);
                draggingScrollbar = true;
                return true;
            }

            if (mx >= LIST_X && mx <= LIST_X + LIST_W - 12 && my >= LIST_Y && my <= LIST_Y + LIST_H) {
                int index = scroll + (int) ((my - LIST_Y) / ROW_H);
                if (index >= 0 && index < filtered.size()) {
                    String entry = filtered.get(index);
                    if (mx >= LIST_X + LIST_W - 70 && mx <= LIST_X + LIST_W - 18) {
                        BlacklistStoreFTB.remove(entry);
                        reloadEntries();
                        FTBToastUtil.show("kineticftb_blacklist_removed", Component.translatable("msg.kineticftb.ftb_item.blacklist_removed"));
                    }
                    return true;
                }
            }
        }
        return super.universalMouseClicked(mx, my, btn);
    }

    @Override
    protected boolean universalMouseReleased(double mx, double my, int btn) {
        draggingScrollbar = false;
        return super.universalMouseReleased(mx, my, btn);
    }

    @Override
    protected boolean universalMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingScrollbar) {
            int trackHeight = LIST_H;
            int visible = trackHeight / ROW_H;
            int max = Math.max(0, filtered.size() - visible);
            int thumb = ScrollUtil.calculateThumbHeight(trackHeight, visible, filtered.size(), 24);
            scroll = ScrollUtil.calculateScrollOffset(my, LIST_Y, trackHeight, thumb, max);
            return true;
        }
        return super.universalMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean universalMouseScrolled(double mx, double my, double d) {
        int visible = LIST_H / ROW_H;
        int max = Math.max(0, filtered.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
