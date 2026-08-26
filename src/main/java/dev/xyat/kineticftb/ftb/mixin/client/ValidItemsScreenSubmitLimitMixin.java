package dev.xyat.kineticftb.ftb.mixin.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.gui.quests.ValidItemsScreen;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.xyat.kineticftb.ftb.api.FTBTaskSubmitHelper;
import dev.xyat.kineticftb.ftb.client.gui.FTBSubmitCountScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ValidItemsScreen.class, remap = false)
public abstract class ValidItemsScreenSubmitLimitMixin extends BaseScreen {
    @Unique
    private ItemTask kineticftb$submitTask;

    @ModifyVariable(method = "<init>", at = @At("TAIL"), argsOnly = true, ordinal = 0)
    public ItemTask kineticftb$captureSubmitTask(ItemTask task) {
        this.kineticftb$submitTask = task;
        return task;
    }

    @Inject(method = "addWidgets", at = @At("TAIL"))
    public void kineticftb$replaceSubmitButton(CallbackInfo ignored) {
        if (!FTBTaskSubmitHelper.isCustomSubmitAllowed(this.kineticftb$submitTask)) {
            return;
        }

        List<Widget> widgets = this.getWidgets();
        if (widgets.size() < 3) {
            return;
        }

        int submitButtonIndex = 2;
        if (!(widgets.get(submitButtonIndex) instanceof Button originalButton)) {
            return;
        }

        SimpleTextButton replacementButton = new SimpleTextButton(
                this,
                Component.translatable("button.kineticftb.ftb.submit.confirm"),
                Color4I.empty()
        ) {
            private void kineticftb$syncBounds() {
                this.posX = originalButton.posX;
                this.posY = originalButton.posY;
                this.width = originalButton.width;
                this.height = originalButton.height;
            }

            @Override
            public int getX() {
                this.kineticftb$syncBounds();
                return super.getX();
            }

            @Override
            public int getY() {
                this.kineticftb$syncBounds();
                return super.getY();
            }

            @Override
            public void onClicked(MouseButton button) {
                if (!FTBTaskSubmitHelper.isCustomSubmitAllowed(kineticftb$submitTask)) {
                    originalButton.onClicked(button);
                    return;
                }

                this.playClickSound();
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.setScreen(new FTBSubmitCountScreen(minecraft.screen, kineticftb$submitTask));
            }

            @Override
            public void addMouseOverText(TooltipList list) {
                list.add(Component.translatable("tip.kineticftb.ftb.submit.button"));
            }

            @Override
            public boolean renderTitleInCenter() {
                return true;
            }
        };

        widgets.set(submitButtonIndex, replacementButton);
    }
}