package com.litestock.scan;

import com.litestock.LiteStock;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 使用 ServerboundBlockEntityTagQueryPacket（F3+i 机制）探测容器内容。
 *
 * <p>核心优化：批量并发发送。F3+i 是无状态查询，服务端会为每个 transactionId
 * 独立回包，因此可以一次性发出多个查询（默认 {@link #BATCH_SIZE}），
 * 收到响应按 transactionId 路由。这比串行"发一个等一个"快 N 倍。
 *
 * <p>每个批次发出后，启动超时计时；批次内所有响应收齐或超时后，立即发送下一批。
 */
public class ContainerProbe {
    private static final ContainerProbe INSTANCE = new ContainerProbe();

    /** 每批并发发送的查询数量。过大可能触发服务端限流，8 是较稳妥的值。 */
    private static final int BATCH_SIZE = 8;
    /** 单个批次的最长等待时间（ticks）。超时后未回包的视为失败，立即发下一批。 */
    private static final int BATCH_TIMEOUT_TICKS = 10;
    /** 同一位置的最小重新探测间隔（毫秒），避免短时间重复查询。 */
    private static final long PROBE_COOLDOWN_MS = 5000;

    private final Set<BlockPos> probeQueue = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, List<ItemStack>> results = new HashMap<>();
    private final Map<BlockPos, Long> lastProbeTime = new HashMap<>();

    /** 当前批次正在等待响应的查询：transactionId -> BlockPos */
    private final Map<Integer, BlockPos> pendingBatch = new ConcurrentHashMap<>();
    private int batchTicks = 0;

    private volatile boolean probing = false;
    private Runnable onComplete = null;
    private Consumer<BlockPos> progressCallback = null;
    private int transactionIdCounter = 0;

    public static ContainerProbe getInstance() {
        return INSTANCE;
    }

    private ContainerProbe() {}

    public void startProbe(List<BlockPos> positions, Runnable callback) {
        probeQueue.clear();
        results.clear();
        onComplete = callback;
        progressCallback = null;
        probing = true;
        pendingBatch.clear();
        batchTicks = 0;

        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            long last = lastProbeTime.getOrDefault(pos, 0L);
            if (now - last > PROBE_COOLDOWN_MS) {
                probeQueue.add(pos.immutable());
            }
        }

        LiteStock.LOGGER.info("Starting tag query probe for {} positions (batch size {})", probeQueue.size(), BATCH_SIZE);
    }

    public void onClientTick() {
        if (!probing) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }

        // 如果当前批次还有未回包的查询，检查是否超时
        if (!pendingBatch.isEmpty()) {
            batchTicks++;
            if (batchTicks >= BATCH_TIMEOUT_TICKS) {
                LiteStock.LOGGER.warn("Batch timeout with {} pending queries, moving to next batch", pendingBatch.size());
                // 超时的位置不写入 results，但触发进度回调
                Set<BlockPos> timedOut = new HashSet<>(pendingBatch.values());
                pendingBatch.clear();
                batchTicks = 0;
                for (BlockPos pos : timedOut) {
                    if (progressCallback != null) progressCallback.accept(pos);
                }
            } else {
                return; // 继续等待当前批次响应
            }
        }

        // 当前批次已清空，发送下一批
        if (probeQueue.isEmpty()) {
            probing = false;
            LiteStock.LOGGER.info("Tag query probe complete: {} results", results.size());
            if (onComplete != null) {
                mc.execute(onComplete);
            }
            return;
        }

        sendNextBatch();
    }

    private void sendNextBatch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        BlockPos origin = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;

        // 从队列中取距离最近的 BATCH_SIZE 个位置
        List<BlockPos> batch = new ArrayList<>();
        while (!probeQueue.isEmpty() && batch.size() < BATCH_SIZE) {
            BlockPos nearest = findNearest(origin);
            if (nearest == null) break;
            probeQueue.remove(nearest);
            batch.add(nearest);
        }

        if (batch.isEmpty()) return;

        batchTicks = 0;
        for (BlockPos pos : batch) {
            int txId = transactionIdCounter++;
            pendingBatch.put(txId, pos.immutable());
            ServerboundBlockEntityTagQueryPacket packet =
                    new ServerboundBlockEntityTagQueryPacket(txId, pos);
            mc.player.connection.send(packet);
        }

        LiteStock.LOGGER.debug("Sent batch of {} queries (pending: {})", batch.size(), pendingBatch.size());
    }

    private BlockPos findNearest(BlockPos origin) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : probeQueue) {
            double dist = pos.distSqr(origin);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    /**
     * 服务端回包时调用。按 transactionId 路由到对应位置。
     */
    public void onTagQueryResponse(ClientboundTagQueryPacket packet) {
        if (!probing) return;

        int txId = packet.getTransactionId();
        BlockPos pos = pendingBatch.remove(txId);
        if (pos == null) return; // 不在当前批次中（可能已超时清理）

        CompoundTag tag = packet.getTag();
        if (tag != null) {
            List<ItemStack> items = parseItemsFromNbt(tag);
            results.put(pos, items);
            lastProbeTime.put(pos, System.currentTimeMillis());
        }

        if (progressCallback != null) {
            progressCallback.accept(pos);
        }

        // 如果当前批次已全部回包，立即发送下一批（不用等下一个 tick）
        if (pendingBatch.isEmpty()) {
            batchTicks = 0;
        }
    }

    private List<ItemStack> parseItemsFromNbt(CompoundTag tag) {
        List<ItemStack> items = new ArrayList<>();
        if (tag == null || !tag.contains("Items")) return items;

        ListTag itemsList = tag.getListOrEmpty("Items");

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag itemTag = itemsList.getCompoundOrEmpty(i);
            if (itemTag.isEmpty()) continue;
            ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, itemTag)
                    .result()
                    .ifPresent(stack -> {
                        if (!stack.isEmpty()) items.add(stack);
                    });
        }

        return items;
    }

    public Map<BlockPos, List<ItemStack>> getResults() {
        return results;
    }

    public boolean isProbing() {
        return probing;
    }

    public int getQueueSize() {
        return probeQueue.size() + pendingBatch.size();
    }

    public void setProgressCallback(Consumer<BlockPos> callback) {
        this.progressCallback = callback;
    }

    public void stop() {
        probing = false;
        probeQueue.clear();
        pendingBatch.clear();
        batchTicks = 0;
        onComplete = null;
        progressCallback = null;
    }
}
