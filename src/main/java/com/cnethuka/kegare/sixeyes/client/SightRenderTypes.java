package com.cnethuka.kegare.sixeyes.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** What the eyes draw in the world: seen through walls (no depth test), see-through, unlit, smooth. A subclass only for the shards. */
final class SightRenderTypes extends RenderType {
    private SightRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    static final Function<ResourceLocation, RenderType> THROUGH_WALLS = Util.memoize(texture -> create("kegare_sight", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    /** Drawn only where something stands in front of it: what's behind a wall, never over what's in plain view. */
    private static final DepthTestStateShard BEHIND = new DepthTestStateShard(">", 516); // GL_GREATER

    static final Function<ResourceLocation, RenderType> BEHIND_WALLS = Util.memoize(texture -> create("kegare_sight_behind", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(BEHIND)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));

    /** Ordinary depth: in the open, round the body, under nothing. */
    static final Function<ResourceLocation, RenderType> IN_THE_OPEN = Util.memoize(texture -> create("kegare_sight_open", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false)));
}
