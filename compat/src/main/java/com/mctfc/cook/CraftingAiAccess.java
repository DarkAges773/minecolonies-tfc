package com.mctfc.cook;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;

/**
 * Cross-mixin accessor for the crafting-AI state the Chef forge driver needs. {@code MixinAbstractEntityAICrafting}
 * implements it on {@code AbstractEntityAICrafting} — where {@code currentRecipeStorage} / {@code currentRequest} /
 * {@code finalizeCraftingTask} are <b>declared</b> — so {@code MixinAbstractEntityAIRequestSmelter} (a subclass) reads
 * them via {@code ((CraftingAiAccess) (Object) this)} instead of {@code @Shadow}-ing inherited members. In this
 * MineColonies dev setup Mixin cannot resolve a {@code @Shadow} of an <b>inherited</b> field/method ("was not located in
 * the target class … No refMap loaded"), so the shadow must live on the class that actually declares them.
 */
public interface CraftingAiAccess
{
    IRecipeStorage mctfc$recipe();

    IRequest<? extends PublicCrafting> mctfc$request();

    IAIState mctfc$finalizeCraft();
}
