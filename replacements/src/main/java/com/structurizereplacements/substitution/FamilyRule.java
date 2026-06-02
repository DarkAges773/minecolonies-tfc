package com.structurizereplacements.substitution;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A material-token cascade derived from a {@code from → to} block rule.
 *
 * <p>When a rule's two block ids differ in exactly one path token (splitting the path on {@code _}
 * and {@code /}), that token pair is the "material" — e.g. {@code minecraft:oak_planks →
 * minecraft:spruce_planks} yields {@code oak → spruce}, and {@code tfc:wood/planks/oak →
 * tfc:wood/planks/spruce} yields {@code oak → spruce} as well. The cascade then rewrites that token
 * in any other block of the source namespace (e.g. {@code oak_stairs → spruce_stairs},
 * {@code stripped_oak_log → stripped_spruce_log}); the engine only applies the result if the target
 * block actually exists.
 */
public record FamilyRule(String fromNamespace, Pattern tokenPattern, String toNamespace, String toToken)
{
    /**
     * Derive a family from a {@code from → to} pair, or empty if they don't differ by exactly one
     * path token (e.g. {@code cobblestone → mossy_cobblestone}, which has differing token counts).
     */
    public static Optional<FamilyRule> derive(final ResourceLocation from, final ResourceLocation to)
    {
        final String[] fromTokens = from.getPath().split("[_/]");
        final String[] toTokens = to.getPath().split("[_/]");
        if (fromTokens.length != toTokens.length)
        {
            return Optional.empty();
        }

        int diffIndex = -1;
        for (int i = 0; i < fromTokens.length; i++)
        {
            if (!fromTokens[i].equals(toTokens[i]))
            {
                if (diffIndex != -1)
                {
                    return Optional.empty(); // more than one differing token -> not a clean material swap
                }
                diffIndex = i;
            }
        }
        if (diffIndex == -1)
        {
            return Optional.empty(); // identical paths -> nothing to cascade
        }

        final String fromToken = fromTokens[diffIndex];
        final String toToken = toTokens[diffIndex];
        if (fromToken.isEmpty() || toToken.isEmpty())
        {
            return Optional.empty();
        }

        // Match the material token only at token boundaries (start/end or _ //).
        final Pattern pattern = Pattern.compile("(?<=^|[_/])" + Pattern.quote(fromToken) + "(?=$|[_/])");
        return Optional.of(new FamilyRule(from.getNamespace(), pattern, to.getNamespace(), toToken));
    }

    /**
     * Apply this cascade to a candidate block id, returning the rewritten id (existence not checked
     * here — the caller verifies the block exists), or empty if it doesn't apply.
     */
    public Optional<ResourceLocation> apply(final ResourceLocation source)
    {
        if (!source.getNamespace().equals(fromNamespace))
        {
            return Optional.empty();
        }
        final Matcher matcher = tokenPattern.matcher(source.getPath());
        if (!matcher.find())
        {
            return Optional.empty();
        }
        final String newPath = matcher.replaceAll(Matcher.quoteReplacement(toToken));
        return Optional.ofNullable(ResourceLocation.tryParse(toNamespace + ":" + newPath));
    }
}
