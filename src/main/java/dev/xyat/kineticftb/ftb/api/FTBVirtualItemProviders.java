package dev.xyat.kineticftb.ftb.api;

import dev.xyat.kineticftb.KineticFTB;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FTB 虚拟物品来源注册表。
 * 外部模组调用：
 * FTBVirtualItemProviders.register(new YourProvider());
 */
public final class FTBVirtualItemProviders {
    private static final List<FTBVirtualItemProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private FTBVirtualItemProviders() {
    }

    public static void register(FTBVirtualItemProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
        unregister(id);
        PROVIDERS.add(provider);
        KineticFTB.LOGGER.info("[KT-FTB虚拟物品] 已注册虚拟物品来源：{}", id);
    }

    public static void unregister(ResourceLocation id) {
        if (id == null) return;
        PROVIDERS.removeIf(provider -> id.equals(provider.id()));
    }

    public static long count(ServerPlayer player, ItemStack filterStack) {
        return query(player, filterStack).count();
    }

    public static QueryResult query(ServerPlayer player, ItemStack filterStack) {
        if (player == null || filterStack == null || filterStack.isEmpty()) {
            return new QueryResult(false, 0L);
        }

        boolean supported = false;
        long total = 0L;
        for (FTBVirtualItemProvider provider : PROVIDERS) {
            try {
                if (!provider.matches(player, filterStack)) continue;
                supported = true;
                long add = Math.max(0L, provider.count(player, filterStack));
                if (add == 0L) continue;
                if (Long.MAX_VALUE - total < add) {
                    return new QueryResult(true, Long.MAX_VALUE);
                }
                total += add;
            } catch (Throwable t) {
                KineticFTB.LOGGER.error("[KT-FTB虚拟物品] 统计虚拟物品失败：{}", provider.id(), t);
            }
        }

        return new QueryResult(supported, total);
    }

    public static long extract(ServerPlayer player, ItemStack filterStack, long amount, boolean simulate) {
        if (player == null || filterStack == null || filterStack.isEmpty() || amount <= 0L) return 0L;

        long remaining = amount;
        long extracted = 0L;

        for (FTBVirtualItemProvider provider : PROVIDERS) {
            if (remaining <= 0L) break;

            try {
                if (!provider.matches(player, filterStack)) continue;
                long taken = Math.min(remaining, Math.max(0L, provider.extract(player, filterStack, remaining, simulate)));
                if (taken == 0L) continue;

                extracted += taken;
                remaining -= taken;

                if (!simulate) {
                    provider.sync(player);
                }
            } catch (Throwable t) {
                KineticFTB.LOGGER.error("[KT-FTB虚拟物品] 扣除虚拟物品失败：{}", provider.id(), t);
            }
        }

        return extracted;
    }

    public record QueryResult(boolean supported, long count) {
    }
}
