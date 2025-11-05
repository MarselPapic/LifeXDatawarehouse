package at.htlle.freq.domain;

import java.util.Locale;

public enum ProjectLifecycleStatus {
    PLANNED,
    ACTIVE,
    MAINTENANCE,
    RETIRED;

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

    public String getDisplayLabel() {
        return switch (this) {
            case PLANNED -> "Planned";
            case ACTIVE -> "Active";
            case MAINTENANCE -> "Maintenance";
            case RETIRED -> "Retired";
        };
    }

    public boolean isOperational() {
        return this == ACTIVE || this == MAINTENANCE;
    }
}
