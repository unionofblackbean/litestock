package com.litestock.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.malilib.render.RenderContext;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

final class HighlightLineBuffer {
    private HighlightLineBuffer() {}

    static void draw(RenderTarget target, Vec3 cameraPos) {
        ChestHighlightRenderer renderer = ChestHighlightRenderer.getInstance();
        if (!renderer.hasBoxesToRender()) return;

        try (RenderContext context = new RenderContext(() -> "litestock:highlight_lines", RenderTypes.LINES.pipeline(), 0)) {
            BufferBuilder builder = context.getBuilder();
            renderer.renderBoxes(cameraPos, builder);
            try (MeshData mesh = builder.build()) {
                if (mesh != null) context.draw(target, mesh, false);
            }
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Could not release the LiteStock highlight buffer", error);
        }
    }
}
