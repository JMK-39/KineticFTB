package dev.xyat.kineticftb.ftb.api;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;

public final class FTBQuestApi {
    private FTBQuestApi() {
    }

    public static long parseQuestId(String value) {
        if (value == null || value.isBlank()) return 0L;
        String text = value.trim();
        try {
            long id = Long.parseLong(text);
            if (id != 0L) return id;
        } catch (NumberFormatException ignored) {
        }
        try {
            long id = Long.parseUnsignedLong(text);
            if (id != 0L) return id;
        } catch (NumberFormatException ignored) {
        }
        if (text.matches("[0-9a-fA-F]{1,16}")) {
            try {
                long id = Long.parseUnsignedLong(text, 16);
                if (id != 0L) return id;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    public static boolean isQuestCompleted(ServerPlayer player, long questId) {
        Quest quest = getQuest(player, questId);
        if (quest == null) return false;
        TeamData data = TeamData.get(player);
        return data != null && data.isCompleted(quest);
    }

    public static String questTitle(ServerPlayer player, long questId) {
        Quest quest = getQuest(player, questId);
        if (quest == null) return Long.toUnsignedString(questId);
        String title = quest.getRawTitle();
        if (title != null && !title.isBlank()) return title;
        return QuestObjectBase.getCodeString(questId);
    }

    private static Quest getQuest(ServerPlayer player, long questId) {
        if (player == null || questId == 0L) return null;
        TeamData data = TeamData.get(player);
        if (data == null || data.getFile() == null) return null;
        return data.getFile().getQuest(questId);
    }
}
