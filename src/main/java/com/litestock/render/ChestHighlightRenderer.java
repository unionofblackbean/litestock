package com.litestock.render;

import com.litestock.config.Configs;
import com.litestock.config.LiteStockConfig;
import com.litestock.scan.SelectionManager;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class ChestHighlightRenderer implements IRenderer {
    private static final ChestHighlightRenderer INSTANCE = new ChestHighlightRenderer();

    private final List<BlockPos> highlightedChests = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> scanningContainers = ConcurrentHashMap.newKeySet();
    private int scannedCount = 0;
    private int totalCount = 0;

    public static ChestHighlightRenderer getInstance() {
        return INSTANCE;
    }

    private ChestHighlightRenderer() {}

    public void setHighlightedChests(List<BlockPos> matched) {
        highlightedChests.clear();
        highlightedChests.addAll(matched);
    }

    public void clear() {
        highlightedChests.clear();
        scanningContainers.clear();
        scannedCount = 0;
        totalCount = 0;
    }

    public int getCount() {
        return highlightedChests.size();
    }

    public List<BlockPos> getHighlightedChests() {
        return highlightedChests;
    }

    public void setScanningContainers(Set<BlockPos> containers) {
        scanningContainers.clear();
        scanningContainers.addAll(containers);
    }

    public void removeScanningContainer(BlockPos pos) {
        scanningContainers.remove(pos);
    }

    public void clearScanningContainers() {
        scanningContainers.clear();
    }

    public void setScanningProgress(int scanned, int total) {
        this.scannedCount = scanned;
        this.totalCount = total;
    }

    public int getScannedCount() {
        return scannedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    @Override
    public void onRenderWorldLast(com.mojang.blaze3d.pipeline.RenderTarget renderTarget,
                                  Matrix4fc poseStack,
                                  net.minecraft.client.renderer.state.level.CameraRenderState camera,
                                  net.minecraft.client.renderer.culling.Frustum frustum,
                                  RenderBuffers renderBuffers,
                                  com.mojang.blaze3d.buffers.GpuBufferSlice gpuBufferSlice,
                                  org.joml.Vector4f vector4f,
                                  ProfilerFiller profiler) {
        LiteStockConfig config = LiteStockConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        profiler.push("litestock_render");

        Vec3 camPos = camera.pos;
        double cx = camPos.x;
        double cy = camPos.y;
        double cz = camPos.z;

        MultiBufferSource.BufferSource bufferSource = renderBuffers.bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.LINES);

        final float expand = 0.002f;

        Color4f highlightColor = Configs.Generic.HIGHLIGHT_COLOR.getColor();
        int hR = (int)(highlightColor.r * 255);
        int hG = (int)(highlightColor.g * 255);
        int hB = (int)(highlightColor.b * 255);
        float lineWidth = (float) Configs.Generic.HIGHLIGHT_LINE_WIDTH.getDoubleValue();

        // 扫描中的容器 - 橙色
        if (!scanningContainers.isEmpty()) {
            for (BlockPos pos : scanningContainers) {
                drawBox(lineConsumer,
                        pos.getX() - expand - cx, pos.getY() - expand - cy, pos.getZ() - expand - cz,
                        pos.getX() + 1 + expand - cx, pos.getY() + 1 + expand - cy, pos.getZ() + 1 + expand - cz,
                        255, 128, 0, 255, 2.0f);
            }
        }

        // 匹配的容器（前N个缺失物品）- 配置颜色
        if (config.highlightEnabled && !highlightedChests.isEmpty()) {
            for (BlockPos pos : highlightedChests) {
                if (scanningContainers.contains(pos)) continue;
                drawBox(lineConsumer,
                        pos.getX() - expand - cx, pos.getY() - expand - cy, pos.getZ() - expand - cz,
                        pos.getX() + 1 + expand - cx, pos.getY() + 1 + expand - cy, pos.getZ() + 1 + expand - cz,
                        hR, hG, hB, 255, lineWidth);
            }
        }

        // 选区框
        SelectionManager sel = SelectionManager.getInstance();
        if (sel.hasPos1() && mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos p1 = sel.getPos1();
            BlockPos p2 = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();

            drawBox(lineConsumer,
                    Math.min(p1.getX(), p2.getX()) - cx, Math.min(p1.getY(), p2.getY()) - cy, Math.min(p1.getZ(), p2.getZ()) - cz,
                    Math.max(p1.getX(), p2.getX()) + 1 - cx, Math.max(p1.getY(), p2.getY()) + 1 - cy, Math.max(p1.getZ(), p2.getZ()) + 1 - cz,
                    51, 204, 255, 255, 3.0f);

            drawBox(lineConsumer,
                    p1.getX() - cx, p1.getY() - cy, p1.getZ() - cz,
                    p1.getX() + 1 - cx, p1.getY() + 1 - cy, p1.getZ() + 1 - cz,
                    51, 255, 77, 255, 4.0f);

            drawBox(lineConsumer,
                    p2.getX() - cx, p2.getY() - cy, p2.getZ() - cz,
                    p2.getX() + 1 - cx, p2.getY() + 1 - cy, p2.getZ() + 1 - cz,
                    255, 102, 102, 255, 4.0f);
        }

        bufferSource.endBatch(RenderTypes.LINES);
        profiler.pop();
    }

    private static void drawBox(VertexConsumer consumer,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                int r, int g, int b, int a, float lineWidth) {
        // 底部4条边
        consumer.addVertex((float)minX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(lineWidth);
        // 顶部4条边
        consumer.addVertex((float)minX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 0, 1).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(-1, 0, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(0, 0, -1).setLineWidth(lineWidth);
        // 4条竖边
        consumer.addVertex((float)minX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)minZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)minY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
        consumer.addVertex((float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, a).setNormal(0, 1, 0).setLineWidth(lineWidth);
    }
}
