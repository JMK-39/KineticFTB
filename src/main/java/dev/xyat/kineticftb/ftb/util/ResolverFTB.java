package dev.xyat.kineticftb.ftb.util;

import dev.xyat.kineticcore.api.client.PinyinUtil;
import dev.xyat.kineticftb.ftb.data.BindingStoreFTB;
import dev.xyat.kineticftb.ftb.data.RefFTB;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResolverFTB {
    private static final int MAX_RESOLVE_CACHE_SIZE = 4096;
    private static final Map<String, List<RefFTB>> RESOLVE_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<RefFTB>> eldest) {
            return size() > MAX_RESOLVE_CACHE_SIZE;
        }
    });

    private ResolverFTB() {
    }

    public static List<RefFTB> findQuestRefs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        String cacheKey = BindingStoreFTB.cacheKeyForStack(stack);
        if (cacheKey.isEmpty()) return List.of();

        List<RefFTB> cached = RESOLVE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<RefFTB> resolved = resolveQuestRefs(stack);
        List<RefFTB> immutable = resolved.isEmpty() ? List.of() : List.copyOf(resolved);
        RESOLVE_CACHE.put(cacheKey, immutable);
        return immutable;
    }

    private static List<RefFTB> resolveQuestRefs(ItemStack stack) {
        List<Long> boundIds = BindingStoreFTB.getBoundQuestIds(stack);

        if (!boundIds.isEmpty()) {
            Map<Long, RefFTB> result = new LinkedHashMap<>();
            for (Long id : boundIds) {
                RefFTB ref = BridgeFTB.getQuestRef(id, "KT绑定");
                if (ref != null) {
                    result.put(ref.id(), ref);
                }
            }
            return new ArrayList<>(result.values());
        }

        return QuestMatchCacheFTB.findFtbItemTaskRefs(stack);
    }

    public static void invalidateCache() {
        RESOLVE_CACHE.clear();
    }

    public static String buildSearchText(String code, String title, String chapter, String source) {
        String raw = (code + " " + title + " " + chapter + " " + source).toLowerCase();
        return raw + " " + PinyinUtil.getSearchData(title) + " " + PinyinUtil.getSearchData(chapter);
    }
}
