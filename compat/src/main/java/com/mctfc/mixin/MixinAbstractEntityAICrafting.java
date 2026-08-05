package com.mctfc.mixin;

import com.mctfc.cook.CraftingAiAccess;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the crafting-AI state ({@code currentRecipeStorage} / {@code currentRequest} / {@code finalizeCraftingTask})
 * via {@link CraftingAiAccess}, so the Chef forge driver ({@code MixinAbstractEntityAIRequestSmelter}) can read them by
 * casting {@code this}. These members are <b>declared here</b> in {@code AbstractEntityAICrafting}, so {@code @Shadow}
 * resolves them as the target's own members — whereas shadowing them from the {@code AbstractEntityAIRequestSmelter}
 * subclass (inherited) fails at apply time in this dev setup. {@code remap = false} — MineColonies' own members.
 */
@Mixin(value = AbstractEntityAICrafting.class, remap = false)
public abstract class MixinAbstractEntityAICrafting implements CraftingAiAccess
{
    @Shadow protected IRecipeStorage currentRecipeStorage;
    @Shadow public IRequest<? extends PublicCrafting> currentRequest;

    @Shadow public abstract IAIState finalizeCraftingTask();

    @Override
    public IRecipeStorage mctfc$recipe()
    {
        return currentRecipeStorage;
    }

    @Override
    public IRequest<? extends PublicCrafting> mctfc$request()
    {
        return currentRequest;
    }

    @Override
    public IAIState mctfc$finalizeCraft()
    {
        return finalizeCraftingTask();
    }
}
