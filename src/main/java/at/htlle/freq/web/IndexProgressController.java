package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.IndexProgress;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing reindex progress polled by the UI.
 */
@RestController
@RequestMapping("/api/index-progress")
public class IndexProgressController {

    /**
     * Reads the current Lucene index progress.
     *
     * <p>Path: {@code GET /api/index-progress}</p>
     * <p>Response: 200 OK with {@link IndexProgress.Status} as JSON.</p>
     */
    @GetMapping
    public IndexProgress.Status getStatus() {
        return IndexProgress.get().status();
    }
}
