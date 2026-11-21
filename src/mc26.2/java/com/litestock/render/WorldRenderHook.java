package com.litestock.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import fi.dy.masa.malilib.interfaces.IRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class WorldRenderHook implements IRenderer {
    @Override
    public void onRenderWorldLast(RenderTarget target, Matrix4fc poseStack, CameraRenderState camera,
                                  Frustum frustum, RenderBuffers buffers, GpuBufferSlice terrainFog,
                                  Vector4f fogColor, ProfilerFiller profiler) {
        HighlightLineBuffer.draw(target, camera.pos);
    }
}
