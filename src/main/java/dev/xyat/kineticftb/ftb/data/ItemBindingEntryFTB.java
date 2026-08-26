package dev.xyat.kineticftb.ftb.data;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;

public class ItemBindingEntryFTB {
    public String key = "";
    public String itemId = "";
    public String nbt = "";
    public boolean matchNbt = false;
    public LinkedHashSet<Long> questIds = new LinkedHashSet<>();

    public ItemBindingEntryFTB() {
    }

    public ItemBindingEntryFTB(String itemId) {
        this.itemId = itemId;
        this.key = makeKey(itemId, "", false);
    }

    public static String makeKey(String itemId, String nbt, boolean matchNbt) {
        String normalizedItem = itemId == null ? "" : itemId.trim();
        String normalizedNbt = normalizeNbt(nbt);
        return matchNbt && !normalizedNbt.isEmpty() ? normalizedItem + "|nbt:" + normalizedNbt : normalizedItem;
    }

    public static String normalizeNbt(String nbt) {
        if (nbt == null) return "";
        String s = nbt.trim();
        if (s.isEmpty() || s.equals("{}")) return "";
        return s;
    }

    public void refreshKey() {
        this.nbt = normalizeNbt(this.nbt);
        this.key = makeKey(this.itemId, this.nbt, this.matchNbt);
    }

    public ItemStack createDisplayStack() {
        return BindingStoreFTB.createDisplayStack(itemId, matchNbt ? nbt : "");
    }

    public int questCount() {
        return questIds == null ? 0 : questIds.size();
    }
}
