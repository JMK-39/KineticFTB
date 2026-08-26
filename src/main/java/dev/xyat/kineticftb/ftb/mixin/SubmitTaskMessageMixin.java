package dev.xyat.kineticftb.ftb.mixin;

import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.net.SubmitTaskMessage;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.xyat.kineticftb.ftb.api.FTBTaskSubmitHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubmitTaskMessage.class, remap = false)
public abstract class SubmitTaskMessageMixin {
    @Shadow
    @Final
    private long taskId;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void kineticftb$rewriteSubmitHandle(NetworkManager.PacketContext context, CallbackInfo ci) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            ci.cancel();
            return;
        }

        TeamData data = TeamData.get(player);
        if (data.isLocked()) {
            ci.cancel();
            return;
        }

        Task task = data.getFile().getTask(this.taskId);
        if (!(task instanceof ItemTask itemTask)) {
            return;
        }

        if (!FTBTaskSubmitHelper.isCustomSubmitAllowed(data, itemTask)) {
            return;
        }

        BaseQuestFile file = data.getFile();
        if (!(file instanceof ServerQuestFile sqf)) {
            ci.cancel();
            return;
        }

        sqf.withPlayerContext(player, () -> FTBTaskSubmitHelper.submit(player, data, itemTask, 1));
        ci.cancel();
    }
}
