package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.preset.Preset;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → client: the active read-only built-in presets ({@link BuiltinPresets}). Like the substitution
 * ruleset, presets load <b>server-side only</b> (datapacks, {@link com.structurizereplacements.preset.BuiltinPresetReloadListener}),
 * so a dedicated server's clients need them pushed to populate the "Presets" picker. Sent on the same triggers
 * as the rule sync (player join and after {@code /reload}); the client just replaces its built-in preset set.
 */
public class SyncBuiltinPresetsMessage
{
    /** Bounded so a hostile peer can't force a huge pre-size allocation from a tiny payload. */
    private static final int MAX_PRESETS = 4096;

    private final List<Preset> presets;

    public SyncBuiltinPresetsMessage(final List<Preset> presets)
    {
        this.presets = presets;
    }

    public SyncBuiltinPresetsMessage(final FriendlyByteBuf buf)
    {
        final int count = buf.readVarInt();
        if (count < 0 || count > MAX_PRESETS)
        {
            throw new DecoderException("Preset count " + count + " out of range (max " + MAX_PRESETS + ")");
        }
        this.presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            final String id = buf.readUtf();
            final var name = buf.readComponent();
            final String folder = buf.readUtf();
            final Block icon = readNullableBlock(buf);
            final Map<Block, Block> picks = ChoiceCodec.read(buf);
            if (!picks.isEmpty())
            {
                this.presets.add(new Preset(id, name, folder, icon, picks, false));
            }
        }
    }

    public void encode(final FriendlyByteBuf buf)
    {
        buf.writeVarInt(presets.size());
        for (final Preset preset : presets)
        {
            buf.writeUtf(preset.id());
            buf.writeComponent(preset.displayName());
            buf.writeUtf(preset.folder());
            writeNullableBlock(buf, preset.icon());
            ChoiceCodec.write(buf, preset.picks());
        }
    }

    private static void writeNullableBlock(final FriendlyByteBuf buf, @Nullable final Block block)
    {
        final ResourceLocation id = block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
        buf.writeBoolean(id != null);
        if (id != null)
        {
            buf.writeResourceLocation(id);
        }
    }

    @Nullable
    private static Block readNullableBlock(final FriendlyByteBuf buf)
    {
        if (!buf.readBoolean())
        {
            return null;
        }
        final ResourceLocation id = buf.readResourceLocation();
        return ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
    }

    public void handle(final Supplier<NetworkEvent.Context> ctx)
    {
        final NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // Single-player shares the integrated server's loaded presets (same-JVM statics); applying this sync
            // would at best be redundant and at worst clobber the live set. Ignore on a memory connection — the
            // same guard the rule sync uses. Dedicated servers (real connection) still apply it.
            if (context.getNetworkManager() != null && context.getNetworkManager().isMemoryConnection())
            {
                return;
            }
            BuiltinPresets.set(presets);
            StructurizeReplacements.LOGGER.info("Synced {} built-in preset(s) from the server.", presets.size());
        });
        context.setPacketHandled(true);
    }
}
