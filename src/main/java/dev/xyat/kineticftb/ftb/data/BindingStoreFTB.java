package dev.xyat.kineticftb.ftb.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.xyat.kineticftb.KineticFTB;
import dev.xyat.kineticftb.ftb.util.QuestMatchCacheFTB;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class BindingStoreFTB {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ItemBindingEntryFTB> BINDINGS = new LinkedHashMap<>();
    private static final Map<String, List<IndexedBinding>> ITEM_INDEX = new HashMap<>();
    private static final Map<String, List<IndexedBinding>> TAG_INDEX = new HashMap<>();

    private BindingStoreFTB() {
    }

    public static void load() {
        BINDINGS.clear();
        ITEM_INDEX.clear();
        TAG_INDEX.clear();

        Path path = getPath();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("entries") || !root.get("entries").isJsonArray()) {
                return;
            }

            ItemBindingEntryFTB[] entries = GSON.fromJson(root.get("entries"), ItemBindingEntryFTB[].class);
            if (entries == null) {
                return;
            }

            for (ItemBindingEntryFTB entry : entries) {
                putValidated(entry);
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务] 读取绑定文件失败: {}", path, e);
        } finally {
            rebuildIndex();
        }
    }

    private static void putValidated(ItemBindingEntryFTB entry) {
        if (entry == null) return;

        if (isValidTarget(entry.itemId)) {
            return;
        }

        if (entry.questIds == null) entry.questIds = new LinkedHashSet<>();
        entry.questIds.removeIf(id -> id == null || id == 0L);

        if (entry.questIds.isEmpty()) {
            return;
        }

        entry.matchNbt = entry.matchNbt && !ItemBindingEntryFTB.normalizeNbt(entry.nbt).isEmpty();
        entry.refreshKey();
        BINDINGS.put(entry.key, entry);
    }

    private static void rebuildIndex() {
        ITEM_INDEX.clear();
        TAG_INDEX.clear();

        for (ItemBindingEntryFTB entry : BINDINGS.values()) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank()) continue;

            String target = entry.itemId.trim();
            IndexedBinding indexed = createIndexed(entry);
            if (target.startsWith("#")) {
                String tagId = target.substring(1);
                if (!tagId.isBlank()) {
                    TAG_INDEX.computeIfAbsent(tagId, key -> new ArrayList<>()).add(indexed);
                }
            } else {
                ITEM_INDEX.computeIfAbsent(target, key -> new ArrayList<>()).add(indexed);
            }
        }
    }

    private static IndexedBinding createIndexed(ItemBindingEntryFTB entry) {
        String normalizedNbt = ItemBindingEntryFTB.normalizeNbt(entry.nbt);
        CompoundTag parsedNbt = null;
        if (!normalizedNbt.isEmpty()) {
            try {
                parsedNbt = TagParser.parseTag(normalizedNbt);
            } catch (Throwable ignored) {
                parsedNbt = null;
            }
        }
        return new IndexedBinding(entry, normalizedNbt, parsedNbt);
    }

    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 2);
            root.add("entries", GSON.toJsonTree(new ArrayList<>(BINDINGS.values())));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            KineticFTB.LOGGER.error("[KT-FTB任务] 保存绑定文件失败: {}", path, e);
        }
    }

    public static Collection<ItemBindingEntryFTB> getAllEntries() {
        return new ArrayList<>(BINDINGS.values());
    }

    public static ItemBindingEntryFTB getOrCreateBaseEntry(ItemStack stack) {
        String itemId = itemKey(stack);
        if (itemId.isEmpty()) return null;

        List<ItemBindingEntryFTB> matches = getMatchingEntries(stack);
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        ItemBindingEntryFTB entry = new ItemBindingEntryFTB(itemId);
        if (stack.hasTag()) {
            entry.nbt = stackNbtString(stack);
            entry.matchNbt = true;
            entry.refreshKey();
        }
        return entry;
    }

    public static ItemBindingEntryFTB copyEntry(ItemBindingEntryFTB entry) {
        ItemBindingEntryFTB copy = new ItemBindingEntryFTB();
        copy.key = entry.key;
        copy.itemId = entry.itemId;
        copy.nbt = entry.nbt;
        copy.matchNbt = entry.matchNbt;
        copy.questIds = new LinkedHashSet<>(entry.questIds);
        return copy;
    }

    public static SaveResult saveEntry(ItemBindingEntryFTB original, ItemBindingEntryFTB edited) {
        if (edited == null) return SaveResult.BAD_ITEM;
        if (isValidTarget(edited.itemId)) return SaveResult.BAD_ITEM;

        edited.matchNbt = edited.matchNbt && !ItemBindingEntryFTB.normalizeNbt(edited.nbt).isEmpty();
        edited.refreshKey();

        if (original != null && original.key != null && !original.key.equals(edited.key)) {
            BINDINGS.remove(original.key);
        }

        if (edited.questIds == null || edited.questIds.isEmpty()) {
            BINDINGS.remove(edited.key);
        } else {
            if (edited.matchNbt && isValidNbt(edited.nbt)) return SaveResult.BAD_NBT;
            BINDINGS.put(edited.key, edited);
        }

        rebuildIndex();
        save();
        QuestMatchCacheFTB.invalidateRuntimeCache();
        return SaveResult.OK;
    }

    public static void deleteEntry(ItemBindingEntryFTB entry) {
        if (entry == null || entry.key == null) return;
        boolean changed = BINDINGS.remove(entry.key) != null;
        if (changed) {
            rebuildIndex();
            save();
            QuestMatchCacheFTB.invalidateRuntimeCache();
        }
    }

    public static List<Long> getBoundQuestIds(ItemStack stack) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (ItemBindingEntryFTB entry : getMatchingEntries(stack)) {
            ids.addAll(entry.questIds);
        }
        return new ArrayList<>(ids);
    }

    public static List<ItemBindingEntryFTB> getMatchingEntries(ItemStack stack) {
        String itemId = itemKey(stack);
        if (itemId.isEmpty()) return List.of();

        ArrayList<IndexedBinding> candidates = new ArrayList<>();
        List<IndexedBinding> direct = ITEM_INDEX.get(itemId);
        if (direct != null) {
            candidates.addAll(direct);
        }

        if (!TAG_INDEX.isEmpty()) {
            stack.getTags().forEach(tag -> {
                List<IndexedBinding> tagged = TAG_INDEX.get(tag.location().toString());
                if (tagged != null) {
                    candidates.addAll(tagged);
                }
            });
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        ArrayList<ItemBindingEntryFTB> result = new ArrayList<>();
        for (IndexedBinding indexed : candidates) {
            if (matchesIndexedBinding(stack, itemId, indexed)) {
                result.add(indexed.entry());
            }
        }
        return result;
    }

    private static boolean matchesIndexedBinding(ItemStack stack, String itemId, IndexedBinding indexed) {
        ItemBindingEntryFTB entry = indexed.entry();
        String normalizedNbt = indexed.normalizedNbt();

        if (isEnchantedBookId(itemId) && !normalizedNbt.isEmpty()) {
            return matchesParsedNbtFuzzy(stack, indexed.parsedNbt());
        }

        if (!entry.matchNbt || normalizedNbt.isEmpty()) {
            return true;
        }

        return matchesParsedNbtFuzzy(stack, indexed.parsedNbt());
    }


    public static ItemBindingEntryFTB getExactEntry(ItemStack stack) {
        String key = exactKeyForStack(stack);
        if (key.isEmpty()) return null;
        ItemBindingEntryFTB entry = BINDINGS.get(key);
        return entry == null ? null : copyEntry(entry);
    }

    public static LinkedHashSet<Long> getExactQuestIds(ItemStack stack) {
        ItemBindingEntryFTB entry = getExactEntry(stack);
        if (entry == null || entry.questIds == null || entry.questIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(entry.questIds);
    }

    public static SaveResult saveExactQuestIds(ItemStack stack, Collection<Long> questIds) {
        ItemBindingEntryFTB entry = createExactEntry(stack);
        if (entry == null) return SaveResult.BAD_ITEM;
        entry.questIds.clear();
        if (questIds != null) {
            for (Long id : questIds) {
                if (id != null && id != 0L) {
                    entry.questIds.add(id);
                }
            }
        }
        return saveEntry(getStoredEntry(entry.key), entry);
    }

    public static ItemBindingEntryFTB createExactEntry(ItemStack stack) {
        String itemId = itemKey(stack);
        if (itemId.isEmpty()) return null;
        ItemBindingEntryFTB entry = new ItemBindingEntryFTB(itemId);
        String nbt = stackNbtString(stack);
        if (!nbt.isEmpty()) {
            entry.nbt = nbt;
            entry.matchNbt = true;
        }
        entry.refreshKey();
        return entry;
    }

    private static ItemBindingEntryFTB getStoredEntry(String key) {
        if (key == null || key.isBlank()) return null;
        return BINDINGS.get(key);
    }

    public static String exactKeyForStack(ItemStack stack) {
        ItemBindingEntryFTB entry = createExactEntry(stack);
        return entry == null ? "" : entry.key;
    }

    public static String itemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    public static String cacheKeyForStack(ItemStack stack) {
        String itemId = itemKey(stack);
        if (itemId.isEmpty()) return "";
        String nbt = stackNbtString(stack);
        return nbt.isEmpty() ? itemId : itemId + "|nbt:" + nbt;
    }

    public static String stackNbtString(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return "";

        String itemId = itemKey(stack);
        switch (itemId) {
            case "tacz:modern_kinetic_gun" -> {
                CompoundTag clean = new CompoundTag();
                if (tag.contains("GunId")) {
                    clean.putString("GunId", tag.getString("GunId"));
                }
                return clean.isEmpty() ? "" : clean.toString();
            }
            case "tacz:attachment" -> {
                CompoundTag clean = new CompoundTag();
                if (tag.contains("AttachmentId")) {
                    clean.putString("AttachmentId", tag.getString("AttachmentId"));
                }
                return clean.isEmpty() ? "" : clean.toString();
            }
            case "tacz:ammo" -> {
                CompoundTag clean = new CompoundTag();
                if (tag.contains("AmmoId")) {
                    clean.putString("AmmoId", tag.getString("AmmoId"));
                }
                return clean.isEmpty() ? "" : clean.toString();
            }
            case "lrtactical:melee" -> {
                CompoundTag clean = new CompoundTag();
                if (tag.contains("MeleeWeaponId")) {
                    clean.putString("MeleeWeaponId", tag.getString("MeleeWeaponId"));
                }
                return clean.isEmpty() ? "" : clean.toString();
            }
            case "minecraft:enchanted_book" -> {
                CompoundTag clean = cleanEnchantedBookTag(stack);
                return clean.isEmpty() ? "" : clean.toString();
            }
        }

        return tag.toString();
    }

    public static boolean isValidTarget(String target) {
        if (target == null || target.isBlank()) return true;
        String value = target.trim();
        if (value.startsWith("#")) {
            return !isValidTagId(value.substring(1));
        }
        return !isValidItemId(value);
    }

    public static boolean isTagTarget(String target) {
        return target != null && target.trim().startsWith("#");
    }

    public static boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        try {
            new ResourceLocation(itemId.trim());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isValidTagId(String tagId) {
        if (tagId == null || tagId.isBlank()) return false;
        try {
            new ResourceLocation(tagId.trim());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean matchesTarget(ItemStack stack, String target) {
        if (stack == null || stack.isEmpty() || target == null || target.isBlank()) return false;
        String value = target.trim();
        if (value.startsWith("#")) {
            String tagId = value.substring(1);
            try {
                ResourceLocation id = new ResourceLocation(tagId);
                return stack.getTags().anyMatch(tag -> tag.location().equals(id));
            } catch (Throwable ignored) {
                return false;
            }
        }
        return itemKey(stack).equals(value);
    }

    public static ItemStack createDisplayStack(String target, String nbtStr) {
        if (target == null || target.isBlank()) return ItemStack.EMPTY;
        String value = target.trim();
        ItemStack stack = ItemStack.EMPTY;
        try {
            if (value.startsWith("#")) {
                ResourceLocation tagId = new ResourceLocation(value.substring(1));
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    ItemStack temp = new ItemStack(item);
                    if (temp.getTags().anyMatch(tag -> tag.location().equals(tagId))) {
                        stack = temp;
                        break;
                    }
                }
            } else {
                ResourceLocation id = new ResourceLocation(value);
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null) stack = new ItemStack(item);
            }
        } catch (Throwable ignored) {
        }

        if (!stack.isEmpty() && nbtStr != null && !nbtStr.isEmpty()) {
            try {
                stack.setTag(TagParser.parseTag(ItemBindingEntryFTB.normalizeNbt(nbtStr)));
            } catch (Throwable ignored) {
            }
        }
        return stack;
    }

    public static boolean isValidNbt(String nbt) {
        String normalized = ItemBindingEntryFTB.normalizeNbt(nbt);
        if (normalized.isEmpty()) return false;
        try {
            TagParser.parseTag(normalized);
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean matchesNbtFuzzy(ItemStack stack, String expectedNbt) {
        String normalized = ItemBindingEntryFTB.normalizeNbt(expectedNbt);
        if (normalized.isEmpty()) return true;
        try {
            return matchesParsedNbtFuzzy(stack, TagParser.parseTag(normalized));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean matchesParsedNbtFuzzy(ItemStack stack, CompoundTag expected) {
        if (expected == null) return false;
        if (expected.isEmpty()) return true;
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag actual = stack.getTag();
        if (actual == null) return false;
        return containsFuzzy(expected, actual);
    }

    public static boolean shouldForceFuzzyNbt(ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) return false;
        String expectedId = itemKey(expected);
        if (!isEnchantedBookId(expectedId) || !expectedId.equals(itemKey(actual))) return false;
        return hasStoredEnchantments(expected);
    }

    public static boolean matchesForcedFuzzyNbt(ItemStack expected, ItemStack actual) {
        if (!shouldForceFuzzyNbt(expected, actual)) return false;
        CompoundTag expectedTag = cleanEnchantedBookTag(expected);
        CompoundTag actualTag = cleanEnchantedBookTag(actual);
        return containsFuzzy(expectedTag, actualTag);
    }

    public static boolean containsFuzzy(CompoundTag expected, CompoundTag actual) {
        if (expected == null || expected.isEmpty()) return true;
        if (actual == null) return false;

        for (String key : expected.getAllKeys()) {
            if (!actual.contains(key)) return false;

            Tag expectedTag = expected.get(key);
            Tag actualTag = actual.get(key);
            if (!tagMatchesFuzzy(expectedTag, actualTag)) {
                return false;
            }
        }

        return true;
    }

    private static boolean tagMatchesFuzzy(Tag expectedTag, Tag actualTag) {
        if (expectedTag == null) return true;
        if (actualTag == null) return false;

        if (expectedTag instanceof CompoundTag expectedCompound && actualTag instanceof CompoundTag actualCompound) {
            return containsFuzzy(expectedCompound, actualCompound);
        }

        if (expectedTag instanceof ListTag expectedList && actualTag instanceof ListTag actualList) {
            return containsListFuzzy(expectedList, actualList);
        }

        return expectedTag.equals(actualTag);
    }

    private static boolean containsListFuzzy(ListTag expected, ListTag actual) {
        if (expected == null || expected.isEmpty()) return true;
        if (actual == null || actual.isEmpty()) return false;

        boolean[] used = new boolean[actual.size()];

        for (Tag expectedTag : expected) {
            boolean matched = false;

            for (int j = 0; j < actual.size(); j++) {
                if (used[j]) continue;

                Tag actualTag = actual.get(j);
                if (tagMatchesFuzzy(expectedTag, actualTag)) {
                    used[j] = true;
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private static boolean isEnchantedBookId(String itemId) {
        return "minecraft:enchanted_book".equals(itemId);
    }

    private static boolean hasStoredEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("StoredEnchantments");
    }

    private static CompoundTag cleanEnchantedBookTag(ItemStack stack) {
        CompoundTag clean = new CompoundTag();
        if (stack == null || stack.isEmpty()) {
            return clean;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("StoredEnchantments")) {
            Tag storedEnchantments = tag.get("StoredEnchantments");
            if (storedEnchantments != null) {
                clean.put("StoredEnchantments", storedEnchantments.copy());
            }
        }

        return clean;
    }

    private static Path getPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("kineticcore")
                .resolve("ftb_item_quest_bindings.json");
    }

    public enum SaveResult {
        OK,
        BAD_ITEM,
        BAD_NBT,
        NO_QUESTS
    }

    private record IndexedBinding(ItemBindingEntryFTB entry, String normalizedNbt, CompoundTag parsedNbt) {
    }
}
