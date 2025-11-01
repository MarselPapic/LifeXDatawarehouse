
package at.htlle.freq.infrastructure.search;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchHit {
    private String id;
    private String type;
    private String text;
    private String snippet;

    public SearchHit() {}

    public SearchHit(String id, String type, String text, String snippet) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.snippet = snippet;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

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
