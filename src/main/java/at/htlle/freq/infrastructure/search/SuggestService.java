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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simple auto-complete implementation backed by Lucene terms.
 */
@Service
public class SuggestService {

    private final LuceneIndexService lucene;
    private static final Logger log = LoggerFactory.getLogger(SuggestService.class);

    /**
     * Creates a suggestion service backed by {@link LuceneIndexService}.
     *
     * @param lucene Lucene index service used to locate the index directory.
     */
    public SuggestService(LuceneIndexService lucene) {
        this.lucene = lucene;
    }

    // Fields used to source suggestion candidates
    private static final List<String> FIELDS = List.of(
            // The aggregated search content is stored in the "content" field during indexing
            "content"
    );

    /**
     * Returns up to {@code max} suggestions whose terms begin with the provided prefix.
     *
     * @param prefix user-entered prefix.
     * @param max maximum number of suggestions to return.
     * @return list of suggestions ordered by discovery.
     */
    public List<String> suggest(String prefix, int max) {
        if (prefix == null) return List.of();
        String pfx = prefix.toLowerCase(Locale.ROOT);
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
                    BytesRef prefixRef = new BytesRef(pfx);
                    if (te.seekCeil(prefixRef) == TermsEnum.SeekStatus.END) continue;

                    BytesRef br = te.term();
                    while (br != null) {
                        String term = br.utf8ToString();
                        if (!term.toLowerCase(Locale.ROOT).startsWith(pfx)) {
                            break;
                        }

                        out.add(term);
                        if (out.size() >= max) break outer;

                        br = te.next();
                    }
                }
            }
        } catch (IOException e) {
            log.warn("SuggestService: failed to read index at {}", indexPath, e);
        }

        return out.stream().limit(max).collect(Collectors.toList());
    }
}
