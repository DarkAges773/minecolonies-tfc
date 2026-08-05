package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.placement.ChoiceCodec;
import com.structurizereplacements.preset.BuiltinPresets;
import com.structurizereplacements.preset.Preset;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server → client: the active read-only built-in presets ({@link BuiltinPresets}). Like the substitution
 * ruleset, presets load <b>server-side only</b> (datapacks, {@link com.structurizereplacements.preset.BuiltinPresetReloadListener}),
 * so a dedicated server's clients need them pushed to populate the "Presets" picker. Sent on the same triggers
 * as the rule sync (player join and after {@code /reload}); the client just replaces its built-in preset set.
 */
public class SyncBuiltinPresetsMessage implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<SyncBuiltinPresetsMessage> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StructurizeReplacements.MODID, "builtin_presets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBuiltinPresetsMessage> STREAM_CODEC =
            StreamCodec.ofMember(SyncBuiltinPresetsMessage::encode, SyncBuiltinPresetsMessage::new);

    /** Bounded so a hostile peer can't force a huge pre-size allocation from a tiny payload. */
    private static final int MAX_PRESETS = 4096;

    private final List<Preset> presets;

    public SyncBuiltinPresetsMessage(final List<Preset> presets)
    {
        this.presets = presets;
    }

    public SyncBuiltinPresetsMessage(final RegistryFriendlyByteBuf buf)
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
            // 1.20.5 removed FriendlyByteBuf#read/writeComponent — a Component now needs the registry-aware
            // ComponentSerialization codec, which is exactly what the RegistryFriendlyByteBuf gives us.
            final var name = ComponentSerialization.STREAM_CODEC.decode(buf);
            final String folder = buf.readUtf();
            final Block icon = readNullableBlock(buf);
            final Map<Block, Block> picks = ChoiceCodec.read(buf);
            if (!picks.isEmpty())
            {
                this.presets.add(new Preset(id, name, folder, icon, picks, false));
            }
        }
    }

    public void encode(final RegistryFriendlyByteBuf buf)
    {
        buf.writeVarInt(presets.size());
        for (final Preset preset : presets)
        {
            buf.writeUtf(preset.id());
            ComponentSerialization.STREAM_CODEC.encode(buf, preset.displayName());
            buf.writeUtf(preset.folder());
            writeNullableBlock(buf, preset.icon());
            ChoiceCodec.write(buf, preset.picks());
        }
    }

    private static void writeNullableBlock(final FriendlyByteBuf buf, @Nullable final Block block)
    {
        final ResourceLocation id = block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
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
        return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    @Override
    public CustomPacketPayload.Type<SyncBuiltinPresetsMessage> type()
    {
        return TYPE;
    }

    public void handle(final IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            // Single-player shares the integrated server's loaded presets (same-JVM statics); applying this sync
            // would at best be redundant and at worst clobber the live set. Ignore on a memory connection — the
            // same guard the rule sync uses. Dedicated servers (real connection) still apply it.
            if (ctx.connection() != null && ctx.connection().isMemoryConnection())
            {
                return;
            }
            BuiltinPresets.set(presets);
            StructurizeReplacements.LOGGER.info("Synced {} built-in preset(s) from the server.", presets.size());
        });
    }
}
