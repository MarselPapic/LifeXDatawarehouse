
package at.htlle.freq.infrastructure.search;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single search result that can be serialized to or deserialized from JSON.
 * <p>
 * The JSON structure contains the fields {@code id}, {@code type}, {@code text} and optionally
 * {@code snippet}. The optional {@code snippet} is included only when contextual text is
 * available for the hit because of {@link JsonInclude#Include#NON_NULL}.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchHit {
    private String id;
    private String type;
    private String text;
    private String snippet;

    /**
     * Creates an empty search hit instance.
     * <p>
     * This constructor is primarily used by serialization frameworks (e.g. Jackson) that require
     * a no-argument constructor before populating the fields individually.
     * </p>
     */
    public SearchHit() {}

    /**
     * Creates a fully configured search hit.
     *
     * @param id      unique identifier of the hit; must not be {@code null} when the hit is used.
     * @param type    logical type or domain of the hit; should align with the backend search
     *                categories.
     * @param text    primary label or title presented to the user; should be non-empty.
     * @param snippet optional contextual text; may be {@code null} or empty when no context is
     *                available.
     */
    public SearchHit(String id, String type, String text, String snippet) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.snippet = snippet;
    }

    /**
     * Returns the unique identifier of the hit.
     *
     * @return the {@code id}; may be {@code null} for deserialized but not yet validated hits.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the hit.
     *
     * @param id identifier to assign; should be non-null for persisted or indexed hits.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the logical type associated with the hit.
     *
     * @return the {@code type}; may be {@code null} when the backend does not categorize results.
     */
    public String getType() {
        return type;
    }

    /**
     * Updates the logical type of the hit.
     *
     * @param type domain or category value; may be {@code null} if categorization is not required.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the primary display text of the hit.
     *
     * @return the {@code text}; expected to be non-empty for user-facing results.
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the primary display text of the hit.
     *
     * @param text descriptive text to show to the user; should not be {@code null}.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Returns the contextual snippet accompanying the hit.
     *
     * @return the {@code snippet}; may be {@code null} or empty when no context is available.
     */
    public String getSnippet() {
        return snippet;
    }

    /**
     * Assigns the contextual snippet of the hit.
     *
     * @param snippet optional contextual information; may be {@code null} or empty.
     */
    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    /**
     * Returns a string representation useful for logging and debugging.
     */
    @Override
    public String toString() {
        return "SearchHit{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", text='" + text + '\'' +
                ", snippet='" + snippet + '\'' +
                '}';
    }
}
