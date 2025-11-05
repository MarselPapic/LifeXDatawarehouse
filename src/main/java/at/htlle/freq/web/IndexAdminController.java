package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Administrativer Controller für Lucene-Reindizierung.
 *
 * <p>Delegiert an den {@link LuceneIndexService}.</p>
 */
@RestController
@RequestMapping("/api/index")
public class IndexAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(IndexAdminController.class);

    private final LuceneIndexService lucene;

    public IndexAdminController(LuceneIndexService lucene) {
        this.lucene = lucene;
    }

    /**
     * Startet einen asynchronen Reindexing-Job.
     *
     * <p>Pfad: {@code POST /api/index/reindex}</p>
     * <p>Request-Body: leer.</p>
     *
     * @param principal optionaler Security-Principal zur Protokollierung.
     *                  Bei {@code null} wird lediglich die Request-ID protokolliert.
     */
    @PostMapping("/reindex")
    public void reindex(Principal principal) {
        String actorDetail = resolveActor(principal);
        LOG.info("Manual reindex requested ({})", actorDetail);

        Runnable task = () -> {
            LOG.info("Manual reindex task started ({})", actorDetail);
            try {
                lucene.reindexAll();
                LOG.info("Manual reindex task completed successfully ({})", actorDetail);
            } catch (Exception e) {
                LOG.error("Manual reindex task failed ({})", actorDetail, e);
            }
        };

        new Thread(task, "manual-reindex").start();
    }

    private String resolveActor(Principal principal) {
        if (principal != null) {
            return "principal=" + principal.getName();
        }
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            return "requestId=" + requestId;
        }
        return "principal=unknown";
    }
}
