package com.mctfc.mixin;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the worker AI's {@code protected} navigation helpers as public accessors so a
 * {@link com.mctfc.furnace.FurnaceBehavior} can drive the worker (via {@code MixinAbstractEntityAIUsesFurnace},
 * which casts to this interface). They must be invoked through an {@code @Invoker} on the class that
 * <b>declares</b> them ({@code AbstractEntityAIBasic}): a plain {@code @Shadow} from the furnace-AI subclass
 * fails to apply because Mixin doesn't resolve inherited members ("method … was not located in the target
 * class"). {@code remap = false}: MineColonies' own methods.
 */
@Mixin(AbstractEntityAIBasic.class)
public interface AbstractEntityAIBasicInvoker
{
    @Invoker(value = "walkToBuilding", remap = false)
    boolean mctfc$walkToBuilding();

    @Invoker(value = "walkToWorkPos", remap = false)
    boolean mctfc$walkToWorkPos(BlockPos pos);

    @Invoker(value = "getFakePlayer", remap = false)
    FakePlayer mctfc$getFakePlayer();
}
