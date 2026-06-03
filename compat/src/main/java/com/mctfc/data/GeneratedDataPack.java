package com.mctfc.data;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A minimal in-memory {@link PackResources} serving server-data JSON we generate at runtime — used so the
 * dynamically-registered {@code mctfc:mortared/*} cobble twins get a {@code mctfc:mortared_cobblestone}
 * block tag and mortar crafting recipes without shipping a JSON file per (unknown-ahead-of-time) twin.
 *
 * <p>Registered via {@code AddPackFindersEvent} as an always-enabled built-in data pack. Only
 * {@link PackType#SERVER_DATA} is served.
 */
public class GeneratedDataPack implements PackResources
{
    private final String id;
    private final Map<ResourceLocation, byte[]> resources = new HashMap<>();

    public GeneratedDataPack(final String id)
    {
        this.id = id;
    }

    /** Add a JSON resource. {@code path} is the full data path, e.g. {@code tags/blocks/foo.json}. */
    public void putJson(final String namespace, final String path, final String json)
    {
        resources.put(new ResourceLocation(namespace, path), json.getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(final String... elements)
    {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(final PackType packType, final ResourceLocation location)
    {
        if (packType != PackType.SERVER_DATA)
        {
            return null;
        }
        final byte[] bytes = resources.get(location);
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(final PackType packType, final String namespace, final String path, final ResourceOutput output)
    {
        if (packType != PackType.SERVER_DATA)
        {
            return;
        }
        final String prefix = path.endsWith("/") ? path : path + "/";
        for (final Map.Entry<ResourceLocation, byte[]> entry : resources.entrySet())
        {
            final ResourceLocation loc = entry.getKey();
            if (loc.getNamespace().equals(namespace) && loc.getPath().startsWith(prefix))
            {
                output.accept(loc, () -> new ByteArrayInputStream(entry.getValue()));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(final PackType packType)
    {
        if (packType != PackType.SERVER_DATA)
        {
            return Set.of();
        }
        return resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(final MetadataSectionSerializer<T> serializer)
    {
        if (serializer == PackMetadataSection.TYPE)
        {
            return (T) new PackMetadataSection(
                    Component.literal("MineColonies x TFC generated data"),
                    SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        }
        return null;
    }

    @Override
    public String packId()
    {
        return id;
    }

    @Override
    public boolean isBuiltin()
    {
        return true;
    }

    @Override
    public void close()
    {
    }
}
