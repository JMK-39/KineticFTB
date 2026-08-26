package dev.xyat.kineticftb.ftb.util;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Optional;

@JeiPlugin
public class JeiHoveredItemFTB implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return new ResourceLocation("kineticftb", "ftb_hovered_item");
    }

    @Override
    public void onRuntimeAvailable(@NotNull IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    public static ItemStack getHoveredItemStack() {
        if (runtime == null) {
            return ItemStack.EMPTY;
        }

        ItemStack recipeStack = getRecipeIngredientUnderMouse();
        if (!recipeStack.isEmpty()) {
            return recipeStack;
        }

        ItemStack ingredientStack = runtime.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (ingredientStack != null && !ingredientStack.isEmpty()) {
            return ingredientStack.copy();
        }

        ItemStack bookmarkStack = runtime.getBookmarkOverlay().getItemStackUnderMouse();
        if (bookmarkStack != null && !bookmarkStack.isEmpty()) {
            return bookmarkStack.copy();
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack getRecipeIngredientUnderMouse() {
        try {
            Object recipesGui = runtime.getClass().getMethod("getRecipesGui").invoke(runtime);
            if (recipesGui == null) {
                return ItemStack.EMPTY;
            }

            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

            ItemStack stack = invokeItemMethod(recipesGui, "getIngredientUnderMouse", VanillaTypes.ITEM_STACK);
            if (!stack.isEmpty()) {
                return stack;
            }

            stack = invokeItemMethod(recipesGui, "getIngredientUnderMouse", mouseX, mouseY);
            if (!stack.isEmpty()) {
                return stack;
            }

            stack = invokeItemMethod(recipesGui, "getIngredientUnderMouse", VanillaTypes.ITEM_STACK, mouseX, mouseY);
            if (!stack.isEmpty()) {
                return stack;
            }

            stack = invokeItemMethod(recipesGui, "getIngredientUnderMouse");
            if (!stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable ignored) {
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack invokeItemMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            return ItemStack.EMPTY;
        }

        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }

            try {
                Object value = method.invoke(target, args);
                ItemStack stack = extractItemStack(value, 0);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack extractItemStack(Object value, int depth) {
        if (value == null || depth > 8) {
            return ItemStack.EMPTY;
        }

        if (value instanceof ItemStack stack) {
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(object -> extractItemStack(object, depth + 1)).orElse(ItemStack.EMPTY);
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object object : iterable) {
                ItemStack stack = extractItemStack(object, depth + 1);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                ItemStack stack = extractItemStack(Array.get(value, i), depth + 1);
                if (!stack.isEmpty()) {
                    return stack;
                }
            }
            return ItemStack.EMPTY;
        }

        String[] noArgMethods = {
                "getItemStack",
                "getStack",
                "getValue",
                "getIngredient",
                "getDisplayedIngredient",
                "getTypedIngredient"
        };

        for (String methodName : noArgMethods) {
            ItemStack stack = invokeNestedNoArg(value, methodName, depth);
            if (!stack.isEmpty()) {
                return stack;
            }
        }

        String[] itemTypeMethods = {
                "getIngredient",
                "getDisplayedIngredient"
        };

        for (String methodName : itemTypeMethods) {
            ItemStack stack = invokeNestedWithItemType(value, methodName, depth);
            if (!stack.isEmpty()) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack invokeNestedNoArg(Object target, String methodName, int depth) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 0) {
                continue;
            }

            try {
                Object value = method.invoke(target);
                if (value == target) {
                    continue;
                }

                ItemStack stack = extractItemStack(value, depth + 1);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack invokeNestedWithItemType(Object target, String methodName, int depth) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                continue;
            }

            try {
                Object value = method.invoke(target, VanillaTypes.ITEM_STACK);
                if (value == target) {
                    continue;
                }

                ItemStack stack = extractItemStack(value, depth + 1);
                if (!stack.isEmpty()) {
                    return stack;
                }
            } catch (Throwable ignored) {
            }
        }

        return ItemStack.EMPTY;
    }
}
