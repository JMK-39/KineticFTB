package dev.xyat.kineticftb.ftb.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticftb.KineticFTB;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class FavoritesStoreFTB {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Long> FAVORITES = new HashMap<>();

    private FavoritesStoreFTB() {
    }

    public static void load() {
        FAVORITES.clear();
        Path path = getPath();
        if (!Files.exists(path)) return;
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && root.has("favorites")) {
                Map<String, Long> loaded = GSON.fromJson(root.get("favorites"), new TypeToken<Map<String, Long>>() {}.getType());
                if (loaded != null) {
                    FAVORITES.putAll(loaded);
                }
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 读取收藏文件失败", e);
        }
    }

    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.add("favorites", GSON.toJsonTree(FAVORITES));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 保存收藏文件失败", e);
        }
    }

    public static long getFavorite(ItemStack stack) {
        return FAVORITES.getOrDefault(BindingStoreFTB.itemKey(stack), 0L);
    }

    public static void setFavorite(ItemStack stack, long questId) {
        if (questId == 0L) {
            FAVORITES.remove(BindingStoreFTB.itemKey(stack));
        } else {
            FAVORITES.put(BindingStoreFTB.itemKey(stack), questId);
        }
        save();
    }

    private static Path getPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("kineticcore")
                .resolve("ftb_quest_favorites.json");
    }
}
