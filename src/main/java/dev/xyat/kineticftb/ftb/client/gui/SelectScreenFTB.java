package dev.xyat.kineticftb.ftb.client.gui;

import dev.xyat.kineticcore.api.client.AdvancedSearchUtil;
import dev.xyat.kineticcore.api.client.AdaptiveItemGridRenderer;
import dev.xyat.kineticcore.api.client.GuiRenderUtil;
import dev.xyat.kineticcore.api.client.ScaledScreen;
import dev.xyat.kineticcore.api.client.ScrollUtil;
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
import dev.xyat.kineticftb.ftb.util.BridgeFTB;
import dev.xyat.kineticftb.ftb.data.FavoritesStoreFTB;
import dev.xyat.kineticftb.ftb.data.RefFTB;

public class SelectScreenFTB extends ScaledScreen {
    private static final int LIST_X = 24;
    private static final int LIST_Y = 88;
    private static final int LIST_W = 792;
    private static final int LIST_H = 330;
    private static final int ROW_H = 36;
    private static final int FAV_W = 40;
    private static final int FAVORITE_ON_COLOR = 0xFF55FF55;
    private static final int FAVORITE_OFF_COLOR = 0xFFCFCFCF;

    private final Screen parent;
    private final ItemStack stack;
    private final List<RefFTB> allRefs;
    private final List<RefFTB> filtered = new ArrayList<>();

    private EditBox searchBox;
    private int scroll;
    private boolean draggingScrollbar;

    public SelectScreenFTB(Screen parent, ItemStack stack, List<RefFTB> refs) {
        super(Component.translatable("screen.kineticftb.ftb.select"));
        this.parent = parent;
        this.stack = stack;
        this.allRefs = new ArrayList<>(refs);
        this.filtered.addAll(refs);
        configureResponsiveCanvas(
                840f,
                470f,
                6
        );
        this.minScale = 0.5f;
    }

    @Override
    protected void initScaled() {
        searchBox = new EditBox(this.font, LIST_X, 54, 520, 20, Component.translatable("gui.kineticftb.search"));
        searchBox.setResponder(s -> {
            applySearch();
            scroll = 0;
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(728, 432, 88, 22)
                .build());
    }

    private void applySearch() {
        filtered.clear();
        String q = searchBox.getValue().trim().toLowerCase();
        for (RefFTB ref : allRefs) {
            if (AdvancedSearchUtil.match(ref.searchText(), q)) {
                filtered.add(ref);
            }
        }
    }

    @Override
    protected void renderScaledBackground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        GuiRenderUtil.drawShadowOverlay(g, vWidth, vHeight);
        GuiRenderUtil.drawStandardPanel(g, 10, 10, 820, 450);

        g.drawString(font, Component.translatable("screen.kineticftb.ftb.select"), 24, 24, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("label.kineticftb.ftb.select.subtitle"), 170, 25, 0xFFFFFFFF, false);
        AdaptiveItemGridRenderer.drawSlot(g, stack, 565, 53);
        g.renderItem(stack, 566, 54);
        g.drawString(font, Component.translatable("label.kineticftb.ftb.item.name", stack.getHoverName().copy().withStyle(ChatFormatting.AQUA)), 588, 59, 0xFFFFFFFF, false);

        renderList(g, mx, my);
    }

    @Override
    protected void renderScaledForeground(@NotNull GuiGraphics g, int mx, int my, float pt) {
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.drawString(font, Component.translatable("placeholder.kineticftb.ftb.select.search"), searchBox.getX() + 6, searchBox.getY() + 6, 0xFF888888, false);
            g.pose().popPose();
        }
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int visible = LIST_H / ROW_H;
        int maxScroll = Math.max(0, filtered.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;

        GuiRenderUtil.drawDarkPanel(g, LIST_X, LIST_Y, LIST_W, LIST_H);

        if (filtered.isEmpty()) {
            g.drawString(font, Component.translatable("label.kineticftb.ftb.empty.search"), LIST_X + 10, LIST_Y + 12, 0xFFFFFFFF, false);
        }

        long favId = FavoritesStoreFTB.getFavorite(stack);
        long actualFavId = favId;
        if (allRefs.stream().noneMatch(r -> r.id() == favId) && !allRefs.isEmpty()) {
            actualFavId = allRefs.get(0).id();
        }

        int start = scroll;
        int end = Math.min(filtered.size(), start + visible);
        for (int i = start; i < end; i++) {
            int rowY = LIST_Y + (i - start) * ROW_H;
            RefFTB ref = filtered.get(i);

            int drawX = LIST_X + 2;
            int drawY = rowY + 2;
            int drawW = LIST_W - 16 - FAV_W;
            int drawH = ROW_H - 2;
            int favX = drawX + drawW + 2;

            boolean hoverRow = GuiRenderUtil.isHovering(mx, my, drawX, drawY, drawW, drawH);
            boolean hoverFav = GuiRenderUtil.isHovering(mx, my, favX, drawY, FAV_W, drawH);
            boolean isFav = ref.id() == actualFavId;

            g.fill(drawX, drawY, drawX + drawW, drawY + drawH, hoverRow ? 0x66336699 : 0x33000000);
            g.renderOutline(drawX, drawY, drawW, drawH, 0xFFFFAA00);

            g.drawString(font, Component.translatable("label.kineticftb.ftb.quest.title", Component.literal(GuiRenderUtil.trimText(font, ref.title(), LIST_W - 80)).withStyle(ChatFormatting.GOLD)), drawX + 8, drawY + 6, 0xFFFFFFFF, false);
            g.drawString(font, Component.translatable("label.kineticftb.ftb.quest.id", Component.literal(ref.code()).withStyle(ChatFormatting.AQUA)), drawX + 8, drawY + 20, 0xFFFFFFFF, false);
            g.drawString(font, Component.translatable("label.kineticftb.ftb.quest.meta", Component.literal(GuiRenderUtil.trimText(font, ref.chapter() + "  ·  " + ref.source(), LIST_W - 230)).withStyle(ChatFormatting.GRAY)), drawX + 190, drawY + 20, 0xFFFFFFFF, false);

            g.fill(favX, drawY, favX + FAV_W, drawY + drawH, hoverFav ? 0x6688AA22 : (isFav ? 0x44668811 : 0x33000000));
            g.renderOutline(favX, drawY, FAV_W, drawH, 0xFFFFAA00);

            Component star = Component.literal(isFav ? "★" : "☆");
            g.drawString(font, star, favX + FAV_W / 2 - font.width(star) / 2, drawY + 13, isFav ? FAVORITE_ON_COLOR : FAVORITE_OFF_COLOR, false);
        }

        int thumb = ScrollUtil.calculateThumbHeight(LIST_H, visible, filtered.size(), 24);
        ScrollUtil.renderScrollbar(g, LIST_X + LIST_W - 8, LIST_Y, 6, LIST_H, thumb, maxScroll, scroll, draggingScrollbar);
    }

    @Override
    protected void renderTooltips(GuiGraphics g, int smx, int smy, int mx, int my) {
        if (smx >= LIST_X && smx <= LIST_X + 520 && smy >= 54 && smy <= 74) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("tip.kineticftb.ftb.search.title"),
                    Component.translatable("tip.kineticftb.ftb.search.desc"),
                    Component.translatable("tip.kineticftb.ftb.search.match")
            ), mx, my);
            return;
        }
        if (smx >= LIST_X && smx <= LIST_X + LIST_W - 10 && smy >= LIST_Y && smy <= LIST_Y + LIST_H) {
            if (smx >= LIST_X + LIST_W - 10 - FAV_W) {
                g.renderComponentTooltip(font, List.of(
                        Component.translatable("tip.kineticftb.ftb.favorite.title"),
                        Component.translatable("tip.kineticftb.ftb.favorite.desc")
                ), mx, my);
            } else {
                g.renderComponentTooltip(font, List.of(
                        Component.translatable("tip.kineticftb.ftb.list.title"),
                        Component.translatable("tip.kineticftb.ftb.list.open"),
                        Component.translatable("tip.kineticftb.ftb.list.multi")
                ), mx, my);
            }
        }
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
                    if (mx >= LIST_X + LIST_W - 12 - FAV_W) {
                        FavoritesStoreFTB.setFavorite(this.stack, filtered.get(index).id());
                    } else {
                        Minecraft.getInstance().setScreen(parent);
                        BridgeFTB.openQuest(filtered.get(index).id());
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
            int visible = LIST_H / ROW_H;
            int maxScroll = Math.max(0, filtered.size() - visible);
            int thumb = ScrollUtil.calculateThumbHeight(LIST_H, visible, filtered.size(), 24);
            scroll = ScrollUtil.calculateScrollOffset(my, LIST_Y, LIST_H, thumb, maxScroll);
            return true;
        }
        return super.universalMouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    protected boolean universalMouseScrolled(double mx, double my, double d) {
        int visible = LIST_H / ROW_H;
        int maxScroll = Math.max(0, filtered.size() - visible);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
