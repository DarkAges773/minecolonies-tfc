package com.structurizereplacements.network;

import com.structurizereplacements.StructurizeReplacements;
import com.structurizereplacements.substitution.BlockSubstitutions;
import com.structurizereplacements.substitution.CandidateRule;
import com.structurizereplacements.substitution.SubstitutionRule;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server → client: the full active substitution ruleset (fixed {@link SubstitutionRule}s + interactive
 * {@link CandidateRule} pools). Rules load <b>server-side only</b> (datapacks, {@code
 * BlockSubstitutionReloadListener}), so on a dedicated server the client previously had none — the build-wand
 * preview and the GUI "Replace" picker silently did nothing (single-player shares the JVM and never noticed).
 * Sent on player join and after {@code /reload} (see {@code ModEvents#onDatapackSync}); the client simply
 * replaces its ruleset with the snapshot. Tag-based matching works client-side because vanilla already syncs
 * tag contents on the same triggers.
 */
public class SyncSubstitutionRulesMessage implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<SyncSubstitutionRulesMessage> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(StructurizeReplacements.MODID, "substitution_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSubstitutionRulesMessage> STREAM_CODEC =
            StreamCodec.ofMember(SyncSubstitutionRulesMessage::encode, SyncSubstitutionRulesMessage::new);

    /** Decode caps — generous (a kitchen-sink pack stack is ~10–20k rules), but bounded so a hostile
     *  peer can't force a multi-GB pre-size allocation from a tiny payload. */
    private static final int MAX_RULES = 65536;
    private static final int MAX_PROPERTIES = 256;

    private final List<SubstitutionRule> rules;
    private final List<CandidateRule> candidates;

    public SyncSubstitutionRulesMessage(final List<SubstitutionRule> rules, final List<CandidateRule> candidates)
    {
        this.rules = rules;
        this.candidates = candidates;
    }

    public SyncSubstitutionRulesMessage(final RegistryFriendlyByteBuf buf)
    {
        final int ruleCount = readCount(buf, MAX_RULES, "Substitution-rule");
        this.rules = new ArrayList<>(ruleCount);
        for (int i = 0; i < ruleCount; i++)
        {
            final Block fromBlock = readNullableBlock(buf);
            final TagKey<Block> fromTag = readNullableTag(buf);
            final Block to = readNullableBlock(buf);
            final Map<String, String> properties = readStringMap(buf);
            final Map<String, String> copyProperties = readStringMap(buf);
            final int priority = buf.readInt();
            if (to != null && (fromBlock != null || fromTag != null))
            {
                this.rules.add(new SubstitutionRule(fromBlock, fromTag, to, properties, copyProperties, priority));
            }
        }

        final int candidateCount = readCount(buf, MAX_RULES, "Candidate-rule");
        this.candidates = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++)
        {
            final Block fromBlock = readNullableBlock(buf);
            final TagKey<Block> fromTag = readNullableTag(buf);
            final TagKey<Block> toTag = readNullableTag(buf);
            final Map<String, String> properties = readStringMap(buf);
            final Map<String, String> copyProperties = readStringMap(buf);
            final int priority = buf.readInt();
            if (toTag != null && (fromBlock != null || fromTag != null))
            {
                this.candidates.add(new CandidateRule(fromBlock, fromTag, toTag, properties, copyProperties, priority));
            }
        }
    }

    public void encode(final RegistryFriendlyByteBuf buf)
    {
        buf.writeVarInt(rules.size());
        for (final SubstitutionRule rule : rules)
        {
            writeNullableBlock(buf, rule.fromBlock());
            writeNullableTag(buf, rule.fromTag());
            writeNullableBlock(buf, rule.to());
            writeStringMap(buf, rule.properties());
            writeStringMap(buf, rule.copyProperties());
            buf.writeInt(rule.priority());
        }

        buf.writeVarInt(candidates.size());
        for (final CandidateRule rule : candidates)
        {
            writeNullableBlock(buf, rule.fromBlock());
            writeNullableTag(buf, rule.fromTag());
            writeNullableTag(buf, rule.toTag());
            writeStringMap(buf, rule.properties());
            writeStringMap(buf, rule.copyProperties());
            buf.writeInt(rule.priority());
        }
    }

    @Override
    public CustomPacketPayload.Type<SyncSubstitutionRulesMessage> type()
    {
        return TYPE;
    }

    public void handle(final IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            // Single-player: the client shares the integrated server's loaded ruleset (same-JVM statics), so
            // applying this sync is at best redundant and at worst clobbers the live rules with a stale/empty
            // snapshot. Ignore it on a memory connection — the same thing TFC does ("Ignored … sync from
            // logical server"). Dedicated servers (real connection) still apply it; that's the whole point.
            if (ctx.connection() != null && ctx.connection().isMemoryConnection())
            {
                return;
            }
            BlockSubstitutions.setRules(rules, candidates);
            StructurizeReplacements.LOGGER.info("Synced {} fixed rule(s), {} candidate rule(s) from the server.",
                    rules.size(), candidates.size());
        });
    }

    // --- buf helpers ----------------------------------------------------------------------------------------
    // These keep the wider FriendlyByteBuf parameter type: RegistryFriendlyByteBuf extends it, so the codec's
    // buffer passes straight through and the helpers stay reusable.

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

    private static void writeNullableTag(final FriendlyByteBuf buf, @Nullable final TagKey<Block> tag)
    {
        buf.writeBoolean(tag != null);
        if (tag != null)
        {
            buf.writeResourceLocation(tag.location());
        }
    }

    @Nullable
    private static TagKey<Block> readNullableTag(final FriendlyByteBuf buf)
    {
        return buf.readBoolean() ? TagKey.create(Registries.BLOCK, buf.readResourceLocation()) : null;
    }

    private static void writeStringMap(final FriendlyByteBuf buf, final Map<String, String> map)
    {
        buf.writeVarInt(map.size());
        map.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeUtf(value);
        });
    }

    private static int readCount(final FriendlyByteBuf buf, final int max, final String what)
    {
        final int count = buf.readVarInt();
        if (count < 0 || count > max)
        {
            throw new DecoderException(what + " count " + count + " out of range (max " + max + ")");
        }
        return count;
    }

    private static Map<String, String> readStringMap(final FriendlyByteBuf buf)
    {
        final int size = readCount(buf, MAX_PROPERTIES, "Property-map");
        if (size == 0)
        {
            return Map.of();
        }
        final Map<String, String> map = new HashMap<>(size);
        for (int i = 0; i < size; i++)
        {
            map.put(buf.readUtf(), buf.readUtf());
        }
        return map;
    }
}
