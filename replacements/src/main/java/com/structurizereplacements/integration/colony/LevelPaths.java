package com.structurizereplacements.integration.colony;

/**
 * Blueprint-path surgery for the colony mods' level-naming convention ({@code "<name><level>.blueprint"}).
 * Shared by the Build Options mixins (both forks) and {@link BuildingChoiceContext} so the defensive
 * two-digit-safe logic lives once.
 */
public final class LevelPaths
{
    private LevelPaths() {}

    /**
     * The blueprint path of the <i>target</i> level (current + 1 when upgradable, else current) — the same
     * next-level path the Build Options window loads for its material list, so the picker lists the blocks
     * about to be built rather than the current tier's.
     *
     * <p>This reconstructs the level-naming convention by string surgery, which is a silent-wrong-behaviour
     * dependency if that convention ever changes. So it is <b>defensive</b>: it only rewrites the level when
     * the path actually ends with the current level number (which also avoids the two-digit pitfall —
     * chopping a single char would mangle level ≥ 10); otherwise it falls back to the current path. A
     * wrong-but-valid current-tier list is an honest degrade; a corrupted path that loads nothing (an empty
     * picker with no error) is not.
     */
    public static String targetLevelPath(final String current, final int currentLevel, final boolean canUpgrade)
    {
        if (current == null || current.isEmpty() || !canUpgrade)
        {
            return current;
        }
        final String currentSuffix = Integer.toString(currentLevel);
        final String base = current.replace(".blueprint", "");
        if (!base.endsWith(currentSuffix))
        {
            // Naming convention not as expected — don't risk constructing a path that loads nothing.
            return current;
        }
        return base.substring(0, base.length() - currentSuffix.length()) + (currentLevel + 1) + ".blueprint";
    }

    /** The blueprint path for a given level: the base path with its trailing level digit replaced by {@code level}. */
    public static String pathForLevel(final String basePath, final int level)
    {
        if (basePath == null || basePath.isEmpty())
        {
            return basePath;
        }
        final String base = basePath.replace(".blueprint", "");
        return base.substring(0, base.length() - 1) + level + ".blueprint";
    }
}
