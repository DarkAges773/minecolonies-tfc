/**
 * Mixins into Structurize.
 *
 * <p>Active mixins:
 * <ul>
 *   <li>{@link com.structurizereplacements.mixin.MixinStructurePlacer} — rewrites the
 *       {@code BlockInfo} argument of {@code StructurePlacer#handleBlockPlacement} to apply
 *       datapack-driven block substitution at placement time.</li>
 * </ul>
 *
 * <p>To add another: inspect the resolved Structurize jar for the real target class/method (do not
 * guess names), add the {@code @Mixin} class here, then list it in
 * {@code resources/structurizereplacements.mixins.json} under {@code "mixins"} (common) or
 * {@code "client"} (GUI/render-only, e.g. the planned placement-preview mixin).
 */
package com.structurizereplacements.mixin;
