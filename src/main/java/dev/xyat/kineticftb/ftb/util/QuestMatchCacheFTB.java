package dev.xyat.kineticftb.ftb.util;

import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.xyat.kineticftb.KineticFTB;
import dev.xyat.kineticftb.ftb.data.BindingStoreFTB;
import dev.xyat.kineticftb.ftb.data.RefFTB;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestMatchCacheFTB {
    private static final int MAX_RUNTIME_CACHE_SIZE = 4096;
    private static final long REBUILD_RETRY_MS = 2000L;
    private static final Map<String, List<RefFTB>> RUNTIME_MATCH_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<RefFTB>> eldest) {
            return size() > MAX_RUNTIME_CACHE_SIZE;
        }
    });

    private static volatile Map<String, List<CachedItemTask>> TASKS_BY_ITEM = Map.of();
    private static volatile List<CachedItemTask> FILTER_TASKS = List.of();
    private static volatile boolean built = false;
    private static volatile boolean building = false;
    private static volatile long lastBuildAttemptMs = 0L;

    private QuestMatchCacheFTB() {
    }

    public static void checkAndRebuild() {
        if (built || building) return;
        long now = System.currentTimeMillis();
        if (now - lastBuildAttemptMs < REBUILD_RETRY_MS) return;
        rebuild();
    }

    public static synchronized void rebuild() {
        if (building) return;
        building = true;
        lastBuildAttemptMs = System.currentTimeMillis();
        RUNTIME_MATCH_CACHE.clear();
        ResolverFTB.invalidateCache();

        try {
            if (!BridgeFTB.exists()) {
                built = false;
                TASKS_BY_ITEM = Map.of();
                FILTER_TASKS = List.of();
                return;
            }

            ClientQuestFile file = ClientQuestFile.INSTANCE;
            if (file == null) {
                built = false;
                TASKS_BY_ITEM = Map.of();
                FILTER_TASKS = List.of();
                return;
            }

            Map<String, List<CachedItemTask>> byItem = new HashMap<>();
            List<CachedItemTask> filterTasks = new ArrayList<>();

            file.forAllQuests(quest -> {
                for (Task task : quest.getTasks()) {
                    if (!(task instanceof ItemTask itemTask)) continue;

                    RefFTB ref = BridgeFTB.getQuestRef(itemTask.getQuest().getId(), "FTB ItemTask");
                    if (ref == null) continue;

                    ItemStack filterStack = itemTask.getItemStack();
                    String itemId = BindingStoreFTB.itemKey(filterStack);
                    boolean isFilter = itemId.startsWith("itemfilters:");
                    boolean matchNbt = readMatchNbt(itemTask);
                    boolean requiresNbt = isFilter || matchNbt || hasRelevantNbt(filterStack, itemId);

                    CachedItemTask cachedTask = new CachedItemTask(itemTask, ref, filterStack.copy(), isFilter, matchNbt, requiresNbt);

                    if (!itemId.isEmpty() && !isFilter) {
                        byItem.computeIfAbsent(itemId, key -> new ArrayList<>()).add(cachedTask);
                    } else {
                        filterTasks.add(cachedTask);
                    }
                }
            });

            Map<String, List<CachedItemTask>> frozen = new HashMap<>();
            byItem.forEach((key, value) -> frozen.put(key, List.copyOf(value)));

            TASKS_BY_ITEM = Map.copyOf(frozen);
            FILTER_TASKS = List.copyOf(filterTasks);
            built = true;
        } catch (Throwable t) {
            built = false;
            TASKS_BY_ITEM = Map.of();
            FILTER_TASKS = List.of();
            RUNTIME_MATCH_CACHE.clear();
            KineticFTB.LOGGER.error("重建任务匹配缓存失败", t);
        } finally {
            building = false;
        }
    }

    public static void invalidateRuntimeCache() {
        RUNTIME_MATCH_CACHE.clear();
        ResolverFTB.invalidateCache();
    }

    public static void invalidateTaskIndex() {
        built = false;
        RUNTIME_MATCH_CACHE.clear();
        ResolverFTB.invalidateCache();
    }

    public static List<RefFTB> findFtbItemTaskRefs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        if (!built) {
            checkAndRebuild();
        }

        if (!built) {
            return List.of();
        }

        String itemId = BindingStoreFTB.itemKey(stack);
        if (itemId.isEmpty()) return List.of();

        String runtimeKey = runtimeKey(stack, itemId);
        List<RefFTB> cached = RUNTIME_MATCH_CACHE.get(runtimeKey);
        if (cached != null) {
            return cached;
        }

        List<RefFTB> result = computeRefsForStack(stack, itemId);
        List<RefFTB> immutable = result.isEmpty() ? List.of() : List.copyOf(result);
        RUNTIME_MATCH_CACHE.put(runtimeKey, immutable);
        return immutable;
    }

    private static List<RefFTB> computeRefsForStack(ItemStack stack, String targetItemId) {
        List<CachedItemTask> idTasks = TASKS_BY_ITEM.get(targetItemId);
        List<CachedItemTask> filterTasks = FILTER_TASKS;

        int expectedSize = (idTasks == null ? 0 : idTasks.size()) + filterTasks.size();
        if (expectedSize <= 0) return List.of();

        ArrayList<RefFTB> exact = new ArrayList<>(expectedSize);

        if (idTasks != null) {
            for (CachedItemTask cachedTask : idTasks) {
                addIfMatches(exact, cachedTask, stack, targetItemId);
            }
        }

        for (CachedItemTask cachedTask : filterTasks) {
            addIfMatches(exact, cachedTask, stack, targetItemId);
        }

        return RefFTB.dedupe(exact);
    }

    private static void addIfMatches(List<RefFTB> result, CachedItemTask cachedTask, ItemStack stack, String targetItemId) {
        try {
            if (matches(cachedTask, stack, targetItemId)) {
                result.add(cachedTask.ref());
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean matches(CachedItemTask cachedTask, ItemStack stack, String targetItemId) {
        if (cachedTask.isFilter()) {
            return cachedTask.task().test(stack);
        }

        if (!BindingStoreFTB.itemKey(cachedTask.filterStack()).equals(targetItemId)) {
            return false;
        }

        if (BindingStoreFTB.shouldForceFuzzyNbt(cachedTask.filterStack(), stack)) {
            return BindingStoreFTB.matchesForcedFuzzyNbt(cachedTask.filterStack(), stack);
        }

        if (targetItemId.startsWith("tacz:") || targetItemId.startsWith("lrtactical:")) {
            return matchSpecialItemPerfectly(cachedTask.filterStack(), stack, targetItemId);
        }

        if (cachedTask.matchNbt()) {
            return BindingStoreFTB.containsFuzzy(cachedTask.filterStack().getTag(), stack.getTag());
        }

        return true;
    }

    private static boolean readMatchNbt(ItemTask itemTask) {
        boolean matchNbt = false;
        try {
            CompoundTag tag = new CompoundTag();
            itemTask.writeData(tag);
            if (tag.contains("match_nbt")) {
                matchNbt = tag.getBoolean("match_nbt") || tag.getString("match_nbt").equalsIgnoreCase("true");
            }
        } catch (Throwable ignored) {
        }

        if (!matchNbt) {
            try {
                java.lang.reflect.Field field = itemTask.getClass().getField("matchNBT");
                Object value = field.get(itemTask);
                matchNbt = value != null && value.toString().equalsIgnoreCase("TRUE");
            } catch (Throwable ignored) {
            }
        }

        return matchNbt;
    }

    private static boolean hasRelevantNbt(ItemStack stack, String itemId) {
        if (stack == null || stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return false;

        if ("minecraft:enchanted_book".equals(itemId) && tag.contains("StoredEnchantments")) return true;
        if ("tacz:modern_kinetic_gun".equals(itemId) && tag.contains("GunId")) return true;
        if ("tacz:attachment".equals(itemId) && tag.contains("AttachmentId")) return true;
        if ("tacz:ammo".equals(itemId) && tag.contains("AmmoId")) return true;
        return "lrtactical:melee".equals(itemId) && tag.contains("MeleeWeaponId");
    }

    private static boolean matchSpecialItemPerfectly(ItemStack expected, ItemStack actual, String itemId) {
        CompoundTag expTag = expected.getTag();
        CompoundTag actTag = actual.getTag();

        switch (itemId) {
            case "tacz:modern_kinetic_gun" -> {
                if (expTag != null && expTag.contains("GunId")) {
                    String expId = expTag.getString("GunId");
                    String actId = actTag != null ? actTag.getString("GunId") : "";
                    return !expId.isEmpty() && expId.equals(actId);
                }
                return true;
            }
            case "tacz:attachment" -> {
                if (expTag != null && expTag.contains("AttachmentId")) {
                    String expId = expTag.getString("AttachmentId");
                    String actId = actTag != null ? actTag.getString("AttachmentId") : "";
                    return !expId.isEmpty() && expId.equals(actId);
                }
                return true;
            }
            case "tacz:ammo" -> {
                if (expTag != null && expTag.contains("AmmoId")) {
                    String expId = expTag.getString("AmmoId");
                    String actId = actTag != null ? actTag.getString("AmmoId") : "";
                    return !expId.isEmpty() && expId.equals(actId);
                }
                return true;
            }
            case "lrtactical:melee" -> {
                if (expTag != null && expTag.contains("MeleeWeaponId")) {
                    String expId = expTag.getString("MeleeWeaponId");
                    String actId = actTag != null ? actTag.getString("MeleeWeaponId") : "";
                    return !expId.isEmpty() && expId.equals(actId);
                }
                return true;
            }
        }

        return BindingStoreFTB.containsFuzzy(expTag, actTag);
    }

    private static String runtimeKey(ItemStack stack, String itemId) {
        List<CachedItemTask> idTasks = TASKS_BY_ITEM.get(itemId);
        boolean needsNbtKey = !FILTER_TASKS.isEmpty();

        if (!needsNbtKey && idTasks != null) {
            for (CachedItemTask task : idTasks) {
                if (task.requiresNbt()) {
                    needsNbtKey = true;
                    break;
                }
            }
        }

        return needsNbtKey ? BindingStoreFTB.cacheKeyForStack(stack) : itemId;
    }

    private record CachedItemTask(ItemTask task, RefFTB ref, ItemStack filterStack, boolean isFilter, boolean matchNbt, boolean requiresNbt) {
    }
}
