package com.mctfc.inventory;

import com.mctfc.cook.DishType;
import com.mctfc.cook.TfcDishes;
import com.mctfc.food.FoodTemplates;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_RECIPE_SAVED;
import static com.minecolonies.api.util.constant.TranslationConstants.UNABLE_TO_ADD_RECIPE_MESSAGE;

/**
 * Recipe-teaching menu for a TFC <b>composed dish</b> — the native-feeling counterpart of MineColonies'
 * {@code ContainerCrafting} (the Chef's "Teach Crafting" grid). The player drags TFC foods into <b>ghost</b>
 * ingredient slots (a stamped count-1 copy; nothing is consumed) exactly like the vanilla recipe-teaching grids; the
 * result slot previews the {@link TfcDishes}-computed dish; and {@link #teach} registers it to the Chef.
 *
 * <p>Two modes by {@link #dishType} — <b>salad</b> ({@link TfcDishes#salad}) and <b>soup</b> ({@link TfcDishes#soup}) —
 * with an <b>identical</b> layout: 5 ingredients + a bowl (a TFC soup is extracted from the pot with a bowl, so it
 * consumes one too; the water is abstracted). Only the valid-ingredient tag and the compute function differ. The
 * layout mirrors TFC's salad GUI ({@code SaladContainer}): ingredients at {@code (44+18i, 24)}, bowl at {@code (44,
 * 56)}, output at {@code (116, 56)} — which is why the screen reuses TFC's {@code salad.png} for both.
 *
 * <p><b>Why the teach is server-side.</b> ItemStack <i>capabilities</i> (TFC's food data) don't ride container slot
 * sync packets (Forge's {@code getShareTag} carries only NBT), so the client's view of the slots/result is
 * caps-stripped. This menu therefore computes the dish and builds the recipe on the <b>server</b>, from its own
 * authoritative (cap-bearing) slots — see {@link #teach}. The client only previews.
 */
public class ComposeDishMenu extends AbstractContainerMenu
{
    /** Number of ingredient slots (both dishes cap at 5; salad allows 1-5, soup 3-5). */
    private static final int INGREDIENT_SLOTS = 5;

    private final DishType dishType;
    private final BlockPos pos;
    private final int      moduleId;
    private final Level    world;

    private final SimpleContainer ingredients = new SimpleContainer(INGREDIENT_SLOTS);
    private final SimpleContainer bowl        = new SimpleContainer(1);
    private final ResultContainer result      = new ResultContainer();

    /** Index of the first player-inventory slot (everything below is the player's real inventory). */
    private final int firstPlayerSlot;

    /** Client factory: the buffer written by {@code NetworkHooks.openScreen} carries {@code (dishType, pos, moduleId)}. */
    public static ComposeDishMenu fromBuffer(final int windowId, final Inventory inv, final FriendlyByteBuf buf)
    {
        final DishType dishType = DishType.byId(buf.readByte());
        final BlockPos pos = buf.readBlockPos();
        final int moduleId = buf.readInt();
        return new ComposeDishMenu(windowId, inv, dishType, pos, moduleId);
    }

    public ComposeDishMenu(final int windowId, final Inventory inv, final DishType dishType, final BlockPos pos, final int moduleId)
    {
        super(ModMenus.COMPOSE_DISH.get(), windowId);
        this.dishType = dishType;
        this.pos = pos;
        this.moduleId = moduleId;
        this.world = inv.player.level();

        // Result preview slot (index 0) — display only.
        addSlot(new Slot(result, 0, 116, 56)
        {
            @Override public boolean mayPickup(final Player p) { return false; }
            @Override public boolean mayPlace(final ItemStack s) { return false; }
        });

        // Ingredient ghost slots (indices 1..5). Both dishes take a bowl; only the valid ingredient tag differs.
        for (int i = 0; i < INGREDIENT_SLOTS; i++)
        {
            addSlot(new GhostSlot(ingredients, i, 44 + i * 18, 24, dishType::accepts));
        }
        // Bowl ghost slot (index 6) — both salad and soup consume a bowl.
        addSlot(new GhostSlot(bowl, 0, 44, 56, TfcDishes::isDishBowl));

        this.firstPlayerSlot = this.slots.size();

        // Player inventory + hotbar (standard 176x166 layout).
        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++)
        {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }

        recompute();
    }

    public DishType getDishType() { return dishType; }

    public BlockPos getPos() { return pos; }

    public int getModuleId() { return moduleId; }

    /**
     * Stamp/clear a ghost slot on click (no consumption), like {@code ContainerCrafting}: a left/right/swap click with
     * a carried item drops a count-1 copy in (if the slot accepts it); empty-handed clears it. Shift-move is disabled.
     */
    @Override
    public void clicked(final int slotId, final int button, final ClickType mode, final Player player)
    {
        if (slotId >= 1 && slotId < firstPlayerSlot)
        {
            if (mode == ClickType.PICKUP || mode == ClickType.PICKUP_ALL || mode == ClickType.SWAP)
            {
                final Slot slot = this.slots.get(slotId);
                final ItemStack carried = getCarried();
                if (!carried.isEmpty() && slot.mayPlace(carried))
                {
                    slot.set(carried.copyWithCount(1));
                }
                else if (carried.isEmpty() && slot.hasItem())
                {
                    slot.set(ItemStack.EMPTY);
                }
                recompute();
            }
            return;
        }
        if (mode == ClickType.QUICK_MOVE)
        {
            return;
        }
        super.clicked(slotId, button, mode, player);
    }

    /** Recompute the preview on the server (caps intact); it syncs to the client as the result-slot icon. */
    private void recompute()
    {
        if (world.isClientSide)
        {
            return;
        }
        result.setItem(0, computeDish());
    }

    /** The non-empty ingredient stacks currently in the ghost slots (server-authoritative, food caps intact). */
    private List<ItemStack> currentIngredients()
    {
        final List<ItemStack> ings = new ArrayList<>();
        for (int i = 0; i < INGREDIENT_SLOTS; i++)
        {
            final ItemStack s = ingredients.getItem(i);
            if (!s.isEmpty())
            {
                ings.add(s);
            }
        }
        return ings;
    }

    private ItemStack computeDish()
    {
        return dishType.compute(currentIngredients(), bowl.getItem(0));
    }

    /**
     * Teach the composed dish to the Chef (server-side). Computes from the authoritative cap-bearing slots, builds a
     * crafting {@link RecipeStorage} (AIR intermediate → the {@code chef_craft} module), and adds it. Plays a
     * success/error sound + chat message, mirroring {@code AddRemoveRecipeMessage}. The Kitchen's {@code EDIBLE}
     * compatibility gate decides accept/reject (TFC food carries a flat vanilla {@code FoodProperties}, so it passes).
     */
    public void teach(final ServerPlayer player)
    {
        final ItemStack output = computeDish();
        if (output.isEmpty())
        {
            SoundUtils.playErrorSound(player, player.blockPosition());
            return;
        }

        // Stamp the food inputs/output as non-decaying *templates* so the recipe-list GUI never renders them spoiled
        // (they're references to a dish, not real food). The actual crafted ingredients/output carry real freshness —
        // MixinRecipeStorage re-stamps/decay-carries the crafted stack. See FoodTemplates.
        final List<ItemStorage> inputs = new ArrayList<>();
        for (final ItemStack s : currentIngredients())
        {
            inputs.add(new ItemStorage(FoodTemplates.nonDecaying(s.copyWithCount(1))));
        }
        if (!bowl.getItem(0).isEmpty())
        {
            inputs.add(new ItemStorage(FoodTemplates.nonDecaying(bowl.getItem(0).copyWithCount(1))));
        }

        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
        if (colony == null)
        {
            return;
        }
        final IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null || !(building.getModule(moduleId) instanceof AbstractCraftingBuildingModule module))
        {
            return;
        }

        // gridSize 3 → AIR intermediate → the crafting module (chef_craft).
        final IRecipeStorage storage = RecipeStorage.builder()
            .withInputs(inputs)
            .withPrimaryOutput(FoodTemplates.nonDecaying(output))
            .withGridSize(3)
            .withIntermediate(Blocks.AIR)
            .build();
        final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
        if (module.addRecipe(token))
        {
            SoundUtils.playSuccessSound(player, player.blockPosition());
            MessageUtils.format(MESSAGE_RECIPE_SAVED).sendTo(player);
        }
        else
        {
            SoundUtils.playErrorSound(player, player.blockPosition());
            MessageUtils.format(UNABLE_TO_ADD_RECIPE_MESSAGE, Component.translatable(building.getBuildingDisplayName())).sendTo(player);
        }
        building.markDirty();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index)
    {
        return ItemStack.EMPTY; // shift-move disabled — this is a template editor, not real crafting
    }

    @Override
    public boolean stillValid(final Player player)
    {
        return true;
    }

    /** A ghost slot: max stack 1, never picked up, never truly removed, accepts only items passing {@code valid}. */
    private static class GhostSlot extends Slot
    {
        private final Predicate<ItemStack> valid;

        GhostSlot(final Container container, final int index, final int x, final int y, final Predicate<ItemStack> valid)
        {
            super(container, index, x, y);
            this.valid = valid;
        }

        @Override public int getMaxStackSize() { return 1; }

        @Override public boolean mayPlace(final ItemStack stack) { return valid.test(stack); }

        @Override public boolean mayPickup(final Player player) { return false; }

        @Override public ItemStack remove(final int amount) { return ItemStack.EMPTY; }
    }
}
