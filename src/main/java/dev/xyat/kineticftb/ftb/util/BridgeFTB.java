package dev.xyat.kineticftb.ftb.util;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.xyat.kineticftb.KineticFTB;

import java.util.ArrayList;
import java.util.List;
import dev.xyat.kineticftb.ftb.data.RefFTB;

public final class BridgeFTB {
    private BridgeFTB() {
    }

    public static void init() {
    }

    public static boolean exists() {
        return ClientQuestFile.exists();
    }

    public static void openQuest(long id) {
        if (id == 0L) {
            return;
        }
        try {
            ClientQuestFile.openBookToQuestObject(id);
        } catch (Throwable t) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 打开 FTB 任务失败: {}", toCodeString(id), t);
        }
    }

    public static List<RefFTB> getAllQuestRefs() {
        List<RefFTB> result = new ArrayList<>();
        if (!exists()) {
            return result;
        }

        try {
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            if (file == null) {
                return result;
            }

            file.forAllQuests(quest -> {
                RefFTB ref = toQuestRef(quest, "FTB任务");
                if (ref != null) {
                    result.add(ref);
                }
            });
        } catch (Throwable t) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 读取全部任务失败", t);
        }

        return RefFTB.dedupe(result);
    }

    public static RefFTB getQuestRef(long id, String source) {
        if (id == 0L || !exists()) {
            return null;
        }

        try {
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            if (file == null) {
                return null;
            }

            return toQuestRef(file.getQuest(id), source);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String toCodeString(long id) {
        return QuestObjectBase.getCodeString(id);
    }

    private static RefFTB toQuestRef(Quest quest, String source) {
        if (quest == null || !quest.isValid()) {
            return null;
        }

        try {
            long id = quest.getId();
            String code = quest.getCodeString();
            String title = safeTitle(quest);
            String chapter = safeChapterTitle(quest);
            return new RefFTB(id, code, title, chapter, source,
                    ResolverFTB.buildSearchText(code, title, chapter, source));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeTitle(QuestObjectBase object) {
        String title = object.getRawTitle();
        return title == null || title.isBlank() ? "未命名任务" : title;
    }

    private static String safeChapterTitle(Quest quest) {
        try {
            Chapter chapter = quest.getQuestChapter();
            if (chapter == null || !chapter.isValid()) {
                return "";
            }

            String title = chapter.getRawTitle();
            return title == null ? "" : title;
        } catch (Throwable ignored) {
            return "";
        }
    }
}
