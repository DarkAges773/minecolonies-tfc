package com.mctfc.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read accessor for the worker citizen the AI drives. {@code AbstractAISkeleton#worker} is the base-most field,
 * several superclasses up from the herder AI — a plain {@code @Shadow} of it from a subclass mixin fails to apply
 * (Mixin doesn't resolve inherited members; see the {@code MixinEntityAIWorkFarmer}/{@code AbstractEntityAIBasicInvoker}
 * notes in CLAUDE.md). An {@code @Accessor} on the class that <b>declares</b> the field works, so
 * {@code MixinAbstractEntityAIHerder} casts {@code this} to this interface to read the worker's held feed item.
 * {@code remap = false}: MineColonies' own field.
 */
@Mixin(AbstractAISkeleton.class)
public interface AbstractAISkeletonAccessor
{
    @Accessor(value = "worker", remap = false)
    AbstractEntityCitizen mctfc$worker();
}
