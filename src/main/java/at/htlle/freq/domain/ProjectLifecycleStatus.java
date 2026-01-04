package at.htlle.freq.domain;

import java.util.Locale;

/**
 * Enumerates the lifecycle phases a {@link Project} can assume. Helper methods
 * offer conversions from free text inputs, derive display labels, and allow
 * consumers to check if a project is currently operational.
 */
public enum ProjectLifecycleStatus {
    OFFERED,
    ACTIVE,
    MAINTENANCE,
    EOL;

    /**
     * Parses a lifecycle status value into the matching enum constant.
     *
     * @param value status value to parse.
     * @return matching enum constant or null when blank.
     */
    public static ProjectLifecycleStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        for (ProjectLifecycleStatus status : values()) {
            if (status.name().equals(upper)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown lifecycle status: " + value);
    }

    /**
     * Returns the Display Label value held by this instance.
     *
     * @return human-readable label for the status.
     */
    public String getDisplayLabel() {
        return switch (this) {
            case OFFERED -> "Offered";
            case ACTIVE -> "Active";
            case MAINTENANCE -> "Maintenance";
            case EOL -> "EOL";
        };
    }

    /**
     * Returns whether Operational is enabled.
     *
     * @return true when the status is operational.
     */
    public boolean isOperational() {
        return this == ACTIVE || this == MAINTENANCE;
    }
}
