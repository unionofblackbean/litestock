package com.litestock.scan;

import com.litestock.LiteStock;
import com.litestock.litematica.ItemAlias;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 超时的查询不会丢弃响应，而是保留等待延迟到达的回包。
 */
public class ContainerProbe {
    private static final ContainerProbe INSTANCE = new ContainerProbe();

    private static final int BATCH_SIZE = 8;
    private static final int BATCH_TIMEOUT_TICKS = 15;
    private static final int MAX_PENDING_AGE_TICKS = 40;
    private static final int MAX_LATE_AGE_TICKS = 80;
    private static final long PROBE_COOLDOWN_MS = 5000;

    private final Set<BlockPos> probeQueue = ConcurrentHashMap.newKeySet();
    private final Map<BlockPos, List<ItemStack>> results = new HashMap<>();
    private final Map<BlockPos, Long> lastProbeTime = new HashMap<>();

    private final Map<Integer, BlockPos> pendingBatch = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingBatchAge = new ConcurrentHashMap<>();
    private final Map<Integer, BlockPos> lateResponses = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lateResponseAge = new ConcurrentHashMap<>();
    private int batchTicks = 0;

    private volatile boolean probing = false;
    private Runnable onComplete = null;
    private Consumer<BlockPos> progressCallback = null;
    private int transactionIdCounter = 0;
    private int totalQueriesSent = 0;
    private int totalResponsesReceived = 0;

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
        pendingBatchAge.clear();
        lateResponses.clear();
        lateResponseAge.clear();
        batchTicks = 0;
        totalQueriesSent = 0;
        totalResponsesReceived = 0;

        long now = System.currentTimeMillis();
        int skipped = 0;
        for (BlockPos pos : positions) {
            long last = lastProbeTime.getOrDefault(pos, 0L);
            if (now - last > PROBE_COOLDOWN_MS) {
                probeQueue.add(pos.immutable());
            } else {
                skipped++;
            }
        }

        LiteStock.LOGGER.info("Starting tag query probe for {} positions (batch size {}, skipped {} due to cooldown)",
                probeQueue.size(), BATCH_SIZE, skipped);
    }

    public void onClientTick() {
        if (!probing) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }

        if (!pendingBatch.isEmpty()) {
            batchTicks++;

            for (Map.Entry<Integer, Integer> entry : new ArrayList<>(pendingBatchAge.entrySet())) {
                int txId = entry.getKey();
                int age = entry.getValue() + 1;
                pendingBatchAge.put(txId, age);
                if (age > MAX_PENDING_AGE_TICKS) {
                    BlockPos pos = pendingBatch.remove(txId);
                    if (pos != null) {
                        pendingBatchAge.remove(txId);
                        lateResponses.put(txId, pos);
                        lateResponseAge.put(txId, 0);
                        if (progressCallback != null) progressCallback.accept(pos);
                        LiteStock.LOGGER.debug("Timed out txId={} pos={} after {} ticks (moved to lateResponses)", txId, pos, age);
                    }
                }
            }

            if (batchTicks >= BATCH_TIMEOUT_TICKS) {
                batchTicks = 0;
            }
        }

        if (!lateResponses.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : new ArrayList<>(lateResponseAge.entrySet())) {
                int txId = entry.getKey();
                int age = entry.getValue() + 1;
                lateResponseAge.put(txId, age);
                if (age > MAX_LATE_AGE_TICKS) {
                    BlockPos pos = lateResponses.remove(txId);
                    if (pos != null) {
                        lateResponseAge.remove(txId);
                        LiteStock.LOGGER.debug("Late response txId={} pos={} dropped after {} total ticks", txId, pos, age);
                    }
                }
            }
        }

        if (!pendingBatch.isEmpty()) {
            return;
        }

        if (probeQueue.isEmpty()) {
            probing = false;
            LiteStock.LOGGER.info("Tag query probe complete: {} results, sent={} responses={}",
                    results.size(), totalQueriesSent, totalResponsesReceived);
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
            pendingBatchAge.put(txId, 0);
            totalQueriesSent++;
            ServerboundBlockEntityTagQueryPacket packet =
                    new ServerboundBlockEntityTagQueryPacket(txId, pos);
            mc.player.connection.send(packet);
        }

        LiteStock.LOGGER.debug("Sent batch of {} queries (total pending: {})", batch.size(), pendingBatch.size());
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

    public void onTagQueryResponse(ClientboundTagQueryPacket packet) {
        if (!probing) return;

        int txId = packet.getTransactionId();
        BlockPos pos = pendingBatch.remove(txId);
        boolean isLate = false;
        if (pos == null) {
            pos = lateResponses.remove(txId);
            if (pos != null) {
                isLate = true;
                lateResponseAge.remove(txId);
                LiteStock.LOGGER.debug("Processing late response txId={} pos={}", txId, pos);
            } else {
                return;
            }
        }
        pendingBatchAge.remove(txId);
        totalResponsesReceived++;

        CompoundTag tag = packet.getTag();
        if (tag != null) {
            List<ItemStack> items = parseItemsFromNbt(tag);
            results.put(pos, items);
            lastProbeTime.put(pos, System.currentTimeMillis());
            if (!items.isEmpty()) {
                LiteStock.LOGGER.debug("Probe pos={}: parsed {} items{}", pos, items.size(), isLate ? " (late)" : "");
            } else {
                LiteStock.LOGGER.debug("Probe pos={}: NBT has no items (tag={}){}", pos, tag.size(), isLate ? " (late)" : "");
            }
        } else {
            results.put(pos, new ArrayList<>());
            LiteStock.LOGGER.debug("Probe pos={}: null tag{}", pos, isLate ? " (late)" : "");
        }

        if (progressCallback != null) {
            progressCallback.accept(pos);
        }
    }

    private List<ItemStack> parseItemsFromNbt(CompoundTag tag) {
        List<ItemStack> items = new ArrayList<>();
        if (tag == null || !tag.contains("Items")) return items;

        ListTag itemsList = tag.getListOrEmpty("Items");
        int codecSuccess = 0;
        int fallbackSuccess = 0;
        int failed = 0;
        int aliased = 0;

        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag itemTag = itemsList.getCompoundOrEmpty(i);
            if (itemTag.isEmpty()) {
                failed++;
                continue;
            }

            Optional<ItemStack> parsed = ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, itemTag).result();
            ItemStack stack = null;
            boolean fromCodec = false;

            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                stack = parsed.get();
                fromCodec = true;
            } else {
                Optional<ItemStack> fallback = tryFallbackParse(itemTag);
                if (fallback.isPresent() && !fallback.get().isEmpty()) {
                    stack = fallback.get();
                }
            }

            if (stack == null || stack.isEmpty()) {
                failed++;
                LiteStock.LOGGER.debug("Failed to parse item #{}", i);
                continue;
            }

            Item originalItem = stack.getItem();
            Identifier origId = BuiltInRegistries.ITEM.getKey(originalItem);
            Item resolvedItem = ItemAlias.resolveItem(origId.toString());
            if (resolvedItem != null && resolvedItem != originalItem) {
                stack = new ItemStack(resolvedItem, stack.getCount());
                aliased++;
            }

            items.add(stack);
            if (fromCodec) codecSuccess++;
            else fallbackSuccess++;
        }

        if (failed > 0 || aliased > 0) {
            LiteStock.LOGGER.debug("NBT parse summary: codec={}, fallback={}, aliased={}, failed={} out of {} items",
                    codecSuccess, fallbackSuccess, aliased, failed, itemsList.size());
        }

        return items;
    }

    private Optional<ItemStack> tryFallbackParse(CompoundTag itemTag) {
        try {
            if (!itemTag.contains("id")) return Optional.empty();

            Optional<String> idOpt = itemTag.getString("id");
            if (idOpt.isEmpty()) return Optional.empty();
            String itemId = idOpt.get();

            if (itemId == null || itemId.isBlank()) return Optional.empty();

            int count = 1;
            if (itemTag.contains("count")) {
                Optional<Integer> countOpt = itemTag.getInt("count");
                if (countOpt.isPresent()) {
                    count = countOpt.get();
                } else {
                    Optional<Byte> byteOpt = itemTag.getByte("Count");
                    if (byteOpt.isPresent()) {
                        count = byteOpt.get();
                    }
                }
            } else if (itemTag.contains("Count")) {
                Optional<Byte> byteOpt = itemTag.getByte("Count");
                if (byteOpt.isPresent()) {
                    count = byteOpt.get();
                }
            }

            if (count <= 0) count = 1;

            Item item = ItemAlias.resolveItem(itemId);
            if (item == null) return Optional.empty();

            ItemStack stack = new ItemStack(item, count);
            return Optional.of(stack);
        } catch (Exception e) {
            return Optional.empty();
        }
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
        pendingBatchAge.clear();
        lateResponses.clear();
        lateResponseAge.clear();
        batchTicks = 0;
        onComplete = null;
        progressCallback = null;
    }
}
