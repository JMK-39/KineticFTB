package dev.xyat.kineticftb.ftb.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.xyat.kineticftb.KineticFTB;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlacklistStoreFTB {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> BLACKLIST = new LinkedHashSet<>();
    private static final Set<String> MOD_INDEX = new HashSet<>();
    private static final Set<String> TAG_INDEX = new HashSet<>();
    private static final Set<String> ITEM_INDEX = new HashSet<>();
    private static final Map<String, List<CompoundTag>> ITEM_NBT_INDEX = new HashMap<>();

    private BlacklistStoreFTB() {
    }

    public static void load() {
        BLACKLIST.clear();
        Path path = getPath();
        if (!Files.exists(path)) {
            rebuildIndex();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            if (array != null) {
                for (JsonElement element : array) {
                    if (element != null && element.isJsonPrimitive()) {
                        String value = element.getAsString();
                        if (value != null && !value.isBlank()) {
                            BLACKLIST.add(value.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 读取黑名单文件失败", e);
        } finally {
            rebuildIndex();
        }
    }

    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            JsonArray array = new JsonArray();
            for (String entry : BLACKLIST) {
                array.add(entry);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(array, writer);
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务物品] 保存黑名单文件失败", e);
        }
    }

    private static void rebuildIndex() {
        MOD_INDEX.clear();
        TAG_INDEX.clear();
        ITEM_INDEX.clear();
        ITEM_NBT_INDEX.clear();

        for (String rule : BLACKLIST) {
            if (rule == null || rule.isBlank()) continue;
            String value = rule.trim();
            if (value.startsWith("@")) {
                String modid = value.substring(1).trim();
                if (!modid.isEmpty()) MOD_INDEX.add(modid);
                continue;
            }
            if (value.startsWith("#")) {
                String tagId = value.substring(1).trim();
                if (!tagId.isEmpty()) TAG_INDEX.add(tagId);
                continue;
            }

            String[] parts = value.split("\\|nbt:", 2);
            String itemId = parts[0].trim();
            if (itemId.isEmpty()) continue;

            if (parts.length > 1) {
                String nbt = ItemBindingEntryFTB.normalizeNbt(parts[1]);
                if (!nbt.isEmpty()) {
                    try {
                        CompoundTag parsed = TagParser.parseTag(nbt);
                        ITEM_NBT_INDEX.computeIfAbsent(itemId, key -> new ArrayList<>()).add(parsed);
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                ITEM_INDEX.add(itemId);
            }
        }
    }

    public static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        String id = BindingStoreFTB.itemKey(stack);
        if (id.isEmpty()) return false;

        if (ITEM_INDEX.contains(id)) return true;

        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String modid = rl != null ? rl.getNamespace() : "";
        if (!modid.isEmpty() && MOD_INDEX.contains(modid)) return true;

        List<CompoundTag> nbtRules = ITEM_NBT_INDEX.get(id);
        if (nbtRules != null) {
            for (CompoundTag expected : nbtRules) {
                if (BindingStoreFTB.matchesParsedNbtFuzzy(stack, expected)) {
                    return true;
                }
            }
        }

        if (!TAG_INDEX.isEmpty() && stack.getTags().anyMatch(tag -> TAG_INDEX.contains(tag.location().toString()))) {
            return true;
        }

        return false;
    }

    public static void add(String target) {
        if (target == null || target.isBlank()) return;
        if (BLACKLIST.add(target.trim())) {
            rebuildIndex();
            save();
        }
    }

    public static void remove(String target) {
        if (target == null || target.isBlank()) return;
        if (BLACKLIST.remove(target.trim())) {
            rebuildIndex();
            save();
        }
    }

    public static List<String> getAll() {
        return new ArrayList<>(BLACKLIST);
    }

    private static Path getPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("kineticcore")
                .resolve("ftb_item_blacklist.json");
    }
}
