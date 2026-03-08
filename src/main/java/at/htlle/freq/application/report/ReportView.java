package at.htlle.freq.application.report;

import java.util.Locale;

/**
 * Supported report views for the reporting module.
 */
public enum ReportView {
    SUPPORT_END("support-end", "Installed software support end dates"),
    LIFECYCLE_STATUS("lifecycle-status", "Lifecycle and install status distribution"),
    ACCOUNT_RISK("account-risk", "Account support risk overview");

    private final String queryValue;
    private final String title;

    ReportView(String queryValue, String title) {
        this.queryValue = queryValue;
        this.title = title;
    }

    /**
     * Returns the query parameter value used by the HTTP API.
     *
     * @return stable query token for this report view.
     */
    public String queryValue() {
        return queryValue;
    }

    /**
     * Returns the human-readable title of the report view.
     *
     * @return display title shown in the reporting UI and exports.
     */
    public String title() {
        return title;
    }

    /**
     * Resolves a report view from an optional query value.
     *
     * @param raw query value, may be null or blank.
     * @return resolved report view, defaults to {@link #SUPPORT_END}.
     */
    public static ReportView fromQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return SUPPORT_END;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ReportView view : values()) {
            if (view.queryValue.equals(normalized)) {
                return view;
            }
        }
        throw new IllegalArgumentException("Unknown report view: " + raw);
    }
}
