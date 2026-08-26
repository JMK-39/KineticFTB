package dev.xyat.kineticftb.ftb.network;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.xyat.kineticftb.KineticFTB;
import dev.xyat.kineticftb.ftb.api.FTBTaskSubmitHelper;
import dev.xyat.kineticftb.ftb.api.FTBVirtualItemProviders;
import dev.xyat.kineticftb.ftb.client.FTBVirtualItemClientState;
import dev.xyat.kineticftb.ftb.client.hud.FTBSubmitResultClientToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public final class FTBSubmitLimitNetwork {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;
    private static int packetId;

    private FTBSubmitLimitNetwork() {
    }

    public static void register() {
        if (channel != null) return;

        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(KineticFTB.MODID, "ftb_submit_limit"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );

        channel.registerMessage(packetId++, ServerboundSubmitTaskWithCount.class,
                ServerboundSubmitTaskWithCount::encode,
                ServerboundSubmitTaskWithCount::decode,
                ServerboundSubmitTaskWithCount::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        channel.registerMessage(packetId++, ClientboundSubmitResult.class,
                ClientboundSubmitResult::encode,
                ClientboundSubmitResult::decode,
                ClientboundSubmitResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        channel.registerMessage(packetId++, ServerboundVirtualItemCountQuery.class,
                ServerboundVirtualItemCountQuery::encode,
                ServerboundVirtualItemCountQuery::decode,
                ServerboundVirtualItemCountQuery::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        channel.registerMessage(packetId++, ClientboundVirtualItemCount.class,
                ClientboundVirtualItemCount::encode,
                ClientboundVirtualItemCount::decode,
                ClientboundVirtualItemCount::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendSubmit(long taskId, int count) {
        if (channel != null) {
            channel.sendToServer(new ServerboundSubmitTaskWithCount(taskId, count));
        }
    }

    public static void sendVirtualItemCountQuery(long taskId) {
        if (channel != null) {
            channel.sendToServer(new ServerboundVirtualItemCountQuery(taskId));
        }
    }

    private static void sendResult(ServerPlayer player, FTBTaskSubmitHelper.Result result) {
        if (channel != null && player != null && result != null) {
            channel.send(PacketDistributor.PLAYER.with(() -> player),
                    new ClientboundSubmitResult(result.requestedTimes(), result.completedTimes(), result.submittedItems()));
        }
    }

    public record ServerboundSubmitTaskWithCount(long taskId, int count) {
        public static void encode(ServerboundSubmitTaskWithCount packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.taskId);
            buffer.writeVarInt(Math.max(1, packet.count));
        }

        public static ServerboundSubmitTaskWithCount decode(FriendlyByteBuf buffer) {
            return new ServerboundSubmitTaskWithCount(buffer.readLong(), Math.max(1, buffer.readVarInt()));
        }

        public static void handle(ServerboundSubmitTaskWithCount packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;

                TeamData data = TeamData.get(player);
                if (data.isLocked()) return;

                Task task = data.getFile().getTask(packet.taskId);
                if (!(task instanceof ItemTask itemTask)) return;
                if (!data.canStartTasks(itemTask.getQuest())) return;

                BaseQuestFile file = data.getFile();
                if (!(file instanceof ServerQuestFile sqf)) return;

                sqf.withPlayerContext(player, () -> {
                    if (!FTBTaskSubmitHelper.isCustomSubmitAllowed(data, itemTask)) {
                        itemTask.submitTask(data, player);
                        return;
                    }

                    FTBTaskSubmitHelper.Result result = FTBTaskSubmitHelper.submit(player, data, itemTask, packet.count);
                    sendResult(player, result);
                });
            });
            context.setPacketHandled(true);
        }
    }

    public record ClientboundSubmitResult(int requestedTimes, int completedTimes, long submittedItems) {
        public static void encode(ClientboundSubmitResult packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(Math.max(1, packet.requestedTimes));
            buffer.writeVarInt(Math.max(0, packet.completedTimes));
            buffer.writeVarLong(Math.max(0L, packet.submittedItems));
        }

        public static ClientboundSubmitResult decode(FriendlyByteBuf buffer) {
            return new ClientboundSubmitResult(
                    Math.max(1, buffer.readVarInt()),
                    Math.max(0, buffer.readVarInt()),
                    Math.max(0L, buffer.readVarLong())
            );
        }

        public static void handle(ClientboundSubmitResult packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> FTBSubmitResultClientToast.show(packet.requestedTimes, packet.completedTimes, packet.submittedItems));
            context.setPacketHandled(true);
        }
    }

    public record ServerboundVirtualItemCountQuery(long taskId) {
        public static void encode(ServerboundVirtualItemCountQuery packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.taskId);
        }

        public static ServerboundVirtualItemCountQuery decode(FriendlyByteBuf buffer) {
            return new ServerboundVirtualItemCountQuery(buffer.readLong());
        }

        public static void handle(ServerboundVirtualItemCountQuery packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;

                boolean supported = false;
                long count = 0L;
                TeamData data = TeamData.get(player);
                Task task = data.getFile().getTask(packet.taskId);
                if (task instanceof ItemTask itemTask && data.canStartTasks(itemTask.getQuest())) {
                    FTBVirtualItemProviders.QueryResult result = FTBVirtualItemProviders.query(player, itemTask.getItemStack());
                    supported = result.supported();
                    count = result.count();
                }

                channel.send(PacketDistributor.PLAYER.with(() -> player),
                        new ClientboundVirtualItemCount(packet.taskId, supported, count));
            });
            context.setPacketHandled(true);
        }
    }

    public record ClientboundVirtualItemCount(long taskId, boolean supported, long count) {
        public static void encode(ClientboundVirtualItemCount packet, FriendlyByteBuf buffer) {
            buffer.writeLong(packet.taskId);
            buffer.writeBoolean(packet.supported);
            buffer.writeVarLong(Math.max(0L, packet.count));
        }

        public static ClientboundVirtualItemCount decode(FriendlyByteBuf buffer) {
            return new ClientboundVirtualItemCount(buffer.readLong(), buffer.readBoolean(), Math.max(0L, buffer.readVarLong()));
        }

        public static void handle(ClientboundVirtualItemCount packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> FTBVirtualItemClientState.accept(packet.taskId, packet.supported, packet.count));
            context.setPacketHandled(true);
        }
    }
}
