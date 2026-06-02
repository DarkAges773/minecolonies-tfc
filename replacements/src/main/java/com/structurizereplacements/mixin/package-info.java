/**
 * Mixins into Structurize.
 *
 * <p>Active mixins:
 * <ul>
 *   <li>{@link com.structurizereplacements.mixin.MixinStructurePlacer} — server side; rewrites the
 *       {@code BlockInfo} argument of {@code StructurePlacer#handleBlockPlacement} so actual
 *       placement uses the substituted block. ({@code remap = false} — Structurize's own method.)</li>
 *   <li>{@link com.structurizereplacements.mixin.MixinBlueprintRenderer} — client side; redirects the
 *       {@code BlockInfo.getState()} read in {@code BlueprintRenderer#init} (the hologram bake) so the
 *       previewed block MODEL is substituted. This is the one that actually changes what you see.
 *       ({@code remap = false} — Structurize members.)</li>
 *   <li>{@link com.structurizereplacements.mixin.MixinBlueprintBlockAccess} — client side; substitutes
 *       {@code BlueprintBlockAccess#getBlockState} (the tint/light/neighbour context the renderer reads)
 *       so context stays consistent with the substituted primary block. (Remapped — overrides vanilla.)</li>
 * </ul>
 *
 * <p>To add another: inspect the resolved Structurize jar for the real target class/method (do not
 * guess names), add the {@code @Mixin} class here, then list it in
 * {@code resources/structurizereplacements.mixins.json} under {@code "mixins"} (common) or
 * {@code "client"} (GUI/render-only, e.g. the planned placement-preview mixin).
 */
package com.structurizereplacements.mixin;
