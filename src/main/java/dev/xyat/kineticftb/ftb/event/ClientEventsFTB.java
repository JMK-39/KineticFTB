package dev.xyat.kineticftb.ftb.event;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xyat.kineticftb.ftb.client.FTBClientConfig;
import dev.xyat.kineticftb.ftb.client.ItemClientModuleFTB;
import dev.xyat.kineticftb.ftb.client.KeyMappingsFTB;
import dev.xyat.kineticftb.ftb.client.gui.SelectScreenFTB;
import dev.xyat.kineticftb.ftb.client.hud.FTBToastUtil;
import dev.xyat.kineticftb.ftb.data.BlacklistStoreFTB;
import dev.xyat.kineticftb.ftb.data.FavoritesStoreFTB;
import dev.xyat.kineticftb.ftb.data.RefFTB;
import dev.xyat.kineticftb.ftb.util.BridgeFTB;
import dev.xyat.kineticftb.ftb.util.HoveredItemFTB;
import dev.xyat.kineticftb.ftb.util.ResolverFTB;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public final class ClientEventsFTB {
    private ClientEventsFTB() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ItemClientModuleFTB.isEnabled()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        HoveredItemFTB.rememberTooltipStack(stack);

        if (FTBClientConfig.shouldSkipTaskJump()) return;
        if (BlacklistStoreFTB.isBlacklisted(stack)) return;

        List<RefFTB> refs = ResolverFTB.findQuestRefs(stack);
        int count = refs.size();
        if (count == 0) return;

        Component singleKeyName = KeyMappingsFTB.OPEN_QUEST.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GOLD);

        event.getToolTip().add(Component.translatable(
                "tip.kineticftb.ftb.open",
                singleKeyName
        ));

        if (count > 1) {
            Component multiKeyName = KeyMappingsFTB.OPEN_QUEST_MULTI.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GOLD);
            Component countText = Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD);
            event.getToolTip().add(Component.translatable(
                    "tip.kineticftb.ftb.open.list",
                    multiKeyName,
                    countText
            ));
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!ItemClientModuleFTB.isEnabled()) return;
        if (FTBClientConfig.shouldSkipTaskJump()) return;

        InputConstants.Key inputKey = InputConstants.getKey(event.getKeyCode(), event.getScanCode());

        boolean isMulti = KeyMappingsFTB.OPEN_QUEST_MULTI.isActiveAndMatches(inputKey);
        boolean isSingle = KeyMappingsFTB.OPEN_QUEST.isActiveAndMatches(inputKey);

        if (isMulti || isSingle) {
            ItemStack hovered = HoveredItemFTB.getHoveredStack(event.getScreen());
            if (hovered.isEmpty()) return;

            if (BlacklistStoreFTB.isBlacklisted(hovered)) return;

            List<RefFTB> refs = ResolverFTB.findQuestRefs(hovered);
            if (refs.isEmpty()) {
                FTBToastUtil.showQuick("kineticftb.ftb.no.quest", Component.translatable("msg.kineticftb.ftb.no.quest"));
                event.setCanceled(true);
                return;
            }

            if (isMulti && refs.size() > 1) {
                Minecraft.getInstance().setScreen(new SelectScreenFTB(event.getScreen(), hovered.copy(), refs));
            } else {
                long favId = FavoritesStoreFTB.getFavorite(hovered);
                long targetId = refs.get(0).id();
                for (RefFTB ref : refs) {
                    if (ref.id() == favId) {
                        targetId = favId;
                        break;
                    }
                }
                BridgeFTB.openQuest(targetId);
            }

            event.setCanceled(true);
        }
    }
}