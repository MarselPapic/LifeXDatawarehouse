package at.htlle.freq.domain;

import java.util.Locale;

/**
 * Defines the visibility state for archived entities.
 */
public enum ArchiveState {
    ACTIVE,
    ARCHIVED,
    ALL;

    /**
     * Parses an archive state value.
     *
     * @param raw raw input value.
     * @return parsed archive state, defaults to {@link #ACTIVE} for null/blank.
     */
    public static ArchiveState from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACTIVE;
        }
        try {
            return ArchiveState.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported archiveState: " + raw, ex);
        }
    }
}
