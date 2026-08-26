package dev.xyat.kineticftb.ftb.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 提供给外部模组注册的 FTB 虚拟物品来源接口。
 * 设计目标：
 * 1. FTB ItemTask 需要统计物品数量时，可以把钱包、虚拟背包、银行等来源一起算进去。
 * 2. FTB ItemTask 需要消耗物品时，可以让外部来源扣除对应数量。
 * 3. KineticFTB 不反向依赖外部模组，外部模组主动注册实现。
 */
public interface FTBVirtualItemProvider {
    /**
     * 提供器唯一 ID，例如 examplemod:virtual_storage。
     */
    ResourceLocation id();

    /**
     * 这个提供器是否愿意处理当前 FTB 任务要求的物品。
     *
     * @param player 当前提交任务的玩家
     * @param filterStack FTB ItemTask 里配置的目标物品
     */
    boolean matches(ServerPlayer player, ItemStack filterStack);

    /**
     * 返回当前玩家在虚拟来源里拥有多少个 filterStack 对应物品。
     * 不要在这里扣除物品。
     */
    long count(ServerPlayer player, ItemStack filterStack);

    /**
     * 从虚拟来源中扣除物品。
     *
     * @param amount 最多需要扣除的数量
     * @param simulate true 表示只模拟，不实际扣除
     * @return 实际可扣除或已经扣除的数量
     */
    long extract(ServerPlayer player, ItemStack filterStack, long amount, boolean simulate);

    /**
     * 扣除后同步客户端显示。没有客户端显示需求可以留空。
     */
    default void sync(ServerPlayer player) {
    }
}
