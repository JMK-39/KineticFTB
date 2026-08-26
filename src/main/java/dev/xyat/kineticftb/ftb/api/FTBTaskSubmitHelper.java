package dev.xyat.kineticftb.ftb.api;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.RandomReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

public final class FTBTaskSubmitHelper {
    private static final int MAX_REQUEST_TIMES = 1_000_000;

    private FTBTaskSubmitHelper() {
    }

    public static boolean isCustomSubmitAllowed(TeamData data, ItemTask task) {
        if (data == null || !isCustomSubmitAllowed(task)) return false;
        return data.canStartTasks(task.getQuest());
    }

    public static boolean isCustomSubmitAllowed(ItemTask task) {
        if (task == null) return false;
        if (task.getQuest() == null) return false;
        if (!task.getQuest().canBeRepeated()) return false;
        if (!task.consumesResources()) return false;
        if (task.isTaskScreenOnly()) return false;
        if (task.isOnlyFromCrafting()) return false;
        if (task.getMaxProgress() <= 0L) return false;
        if (task.getItemStack().isEmpty()) return false;
        if (!isSingleTaskQuest(task)) return false;
        if (!hasSingleAcceptedDisplayItem(task)) return false;
        return hasSingleSupportedReward(task);
    }

    public static Result submit(ServerPlayer player, TeamData data, ItemTask task, int requestedTimes) {
        int requested = clampTimes(requestedTimes);
        if (player == null || !isCustomSubmitAllowed(data, task)) {
            return new Result(requested, 0, 0L);
        }

        Quest quest = task.getQuest();
        int completedTimes = 0;
        long submittedItems = 0L;

        for (int i = 0; i < requested; i++) {
            if (!isCustomSubmitAllowed(data, task)) {
                break;
            }

            if (data.isCompleted(quest) || data.isCompleted(task)) {
                break;
            }

            long maxProgress = Math.max(1L, task.getMaxProgress());
            long progress = Math.max(0L, Math.min(maxProgress, data.getProgress(task)));
            long required = maxProgress - progress;

            if (required <= 0L) {
                break;
            }

            int completionCountBefore = data.getCompletionCount(quest);
            long removed = removeMatchingItems(player, task, required);
            if (removed <= 0L) {
                break;
            }

            submittedItems += removed;
            data.addProgress(task, removed);

            int completionCountAfter = data.getCompletionCount(quest);
            if (completionCountAfter > completionCountBefore) {
                completedTimes += completionCountAfter - completionCountBefore;
                continue;
            }

            if (!data.isCompleted(quest)) {
                break;
            }

            if (!claimSingleRewardForRepeat(player, data, quest)) {
                break;
            }

            completedTimes++;

            if (data.isCompleted(quest)) {
                break;
            }
        }

        return new Result(requested, completedTimes, submittedItems);
    }

    private static boolean isSingleTaskQuest(ItemTask task) {
        try {
            Collection<?> tasks = task.getQuest().getTasks();
            return tasks.size() == 1 && tasks.contains(task);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasSingleAcceptedDisplayItem(ItemTask task) {
        try {
            List<ItemStack> validItems = task.getValidDisplayItems();
            if (validItems.size() != 1) return false;
            ItemStack stack = validItems.get(0);
            return stack != null && !stack.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasSingleSupportedReward(ItemTask task) {
        try {
            Collection<Reward> rewards = task.getQuest().getRewards();
            if (rewards.size() != 1) return false;
            return isSupportedReward(rewards.iterator().next());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isSupportedReward(Reward reward) {
        if (reward == null) return false;
        Class<?> rewardClass = reward.getClass();
        return rewardClass == ItemReward.class || rewardClass == RandomReward.class;
    }

    private static long removeMatchingItems(ServerPlayer player, ItemTask task, long amount) {
        if (amount <= 0L) return 0L;

        Inventory inventory = player.getInventory();
        long remaining = amount;
        long removed = 0L;

        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0L; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!matches(task, stack)) continue;

            int take = (int) Math.min(stack.getCount(), remaining);
            if (take <= 0) continue;

            stack.shrink(take);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }

            remaining -= take;
            removed += take;
        }

        if (removed > 0L) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }

        if (remaining > 0L) {
            long virtualRemoved = FTBVirtualItemProviders.extract(player, task.getItemStack(), remaining, false);
            removed += virtualRemoved;
        }

        return removed;
    }

    private static boolean matches(ItemTask task, ItemStack stack) {
        try {
            return task.test(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean claimSingleRewardForRepeat(ServerPlayer player, TeamData data, Quest quest) {
        if (!quest.canBeRepeated()) return false;
        if (!data.isCompleted(quest)) return false;

        Collection<Reward> rewards = quest.getRewards();
        if (rewards.size() != 1) return false;

        Reward reward = rewards.iterator().next();
        if (!isSupportedReward(reward)) return false;

        data.claimReward(player, reward, true);
        return !data.isCompleted(quest);
    }

    private static int clampTimes(int value) {
        return Math.max(1, Math.min(MAX_REQUEST_TIMES, value));
    }

    public record Result(int requestedTimes, int completedTimes, long submittedItems) {
    }
}
