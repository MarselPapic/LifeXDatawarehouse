package at.htlle.freq.infrastructure.search;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Einfache Autovervollständigung über Lucene-Terms.
 */
@Service
public class SuggestService {

    private final LuceneIndexService lucene;

    public SuggestService(LuceneIndexService lucene) {
        this.lucene = lucene;
    }

    // Felder, aus denen Vorschläge kommen sollen
    private static final List<String> FIELDS = List.of(
            // The aggregated search content is stored in the "content" field when indexing
            "content"
    );

    /** Liefert bis zu {@code max} Vorschläge für das Präfix {@code prefix}. */
    public List<String> suggest(String prefix, int max) {
        if (prefix == null) return List.of();
        String pfx = prefix.toLowerCase();
        if (pfx.length() < 2) return List.of();
        if (max <= 0) return List.of();

        Set<String> out = new LinkedHashSet<>();

        Path indexPath = lucene.getIndexPath();

        try (Directory dir = FSDirectory.open(indexPath);
             DirectoryReader rd = DirectoryReader.open(dir)) {

            outer:
            for (String field : FIELDS) {
                for (LeafReaderContext leaf : rd.leaves()) {
                    Terms terms = leaf.reader().terms(field);
                    if (terms == null) continue;

                    TermsEnum te = terms.iterator();
                    BytesRef br;
                    while ((br = te.next()) != null) {
                        String term = br.utf8ToString();
                        // Case-insensitive Vergleich, Originalwert zurückgeben
                        if (term.toLowerCase().startsWith(pfx)) {
                            out.add(term);
                            if (out.size() >= max) break outer;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Optional: Logging hinzufügen, wenn gewünscht
            // log.warn("SuggestService: Konnte Index nicht lesen", ignored);
        }

        return out.stream().limit(max).collect(Collectors.toList());
    }
}
