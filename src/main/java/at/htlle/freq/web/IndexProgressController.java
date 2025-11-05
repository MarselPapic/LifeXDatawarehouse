package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.IndexProgress;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-Controller für den Reindex-Fortschritt, der von der UI gepollt wird.
 */
@RestController
@RequestMapping("/api/index-progress")
public class IndexProgressController {

    /**
     * Liest den aktuellen Lucene-Index-Fortschritt.
     *
     * <p>Pfad: {@code GET /api/index-progress}</p>
     * <p>Response: 200 OK mit {@link IndexProgress.Status} als JSON.</p>
     */
    @GetMapping
    public IndexProgress.Status getStatus() {
        return IndexProgress.get().status();
    }
}
