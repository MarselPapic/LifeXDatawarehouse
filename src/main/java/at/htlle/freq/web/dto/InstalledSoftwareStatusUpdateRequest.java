package at.htlle.freq.web.dto;

/**
 * Request payload to update the status of a site/software installation link.
 */
public record InstalledSoftwareStatusUpdateRequest(String status) {
    /**
     * Returns the trimmed status value or {@code null} when absent.
     */
    public String normalizedStatus() {
        return status == null ? null : status.trim();
    }
}
