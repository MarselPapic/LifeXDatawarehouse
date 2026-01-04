package at.htlle.freq.web;

import at.htlle.freq.infrastructure.lucene.LuceneIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * Administrative controller for Lucene reindexing.
 *
 * <p>Delegates to {@link LuceneIndexService}.</p>
 */
@RestController
@RequestMapping("/api/index")
public class IndexAdminController {

    private static final Logger LOG = LoggerFactory.getLogger(IndexAdminController.class);

    private final LuceneIndexService lucene;
    private final TaskExecutor taskExecutor;

    /**
     * Creates an admin controller for triggering Lucene reindexing.
     *
     * @param lucene service that performs indexing work.
     * @param taskExecutor executor for running reindexing asynchronously.
     */
    public IndexAdminController(LuceneIndexService lucene, TaskExecutor taskExecutor) {
        this.lucene = lucene;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Starts an asynchronous reindexing job.
     *
     * <p>Path: {@code POST /api/index/reindex}</p>
     * <p>Request body: empty.</p>
     *
     * @param principal optional security principal used for logging.
     *                  When {@code null}, only the request ID is logged.
     */
    @PostMapping("/reindex")
    public void reindex(Principal principal) {
        String actorDetail = resolveActor(principal);
        if (principal != null) {
            MDC.put("principal", principal.getName());
        }
        try {
            LOG.info("Manual reindex requested ({})", actorDetail);

            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            Runnable task = () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                } else {
                    MDC.clear();
                }
                try {
                    LOG.info("Manual reindex task started ({})", actorDetail);
                    lucene.reindexAll();
                    LOG.info("Manual reindex task completed successfully ({})", actorDetail);
                } catch (Exception e) {
                    LOG.error("Manual reindex task failed ({})", actorDetail, e);
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };

            taskExecutor.execute(task);
        } finally {
            if (principal != null) {
                MDC.remove("principal");
            }
        }
    }

    /**
     * Resolves an actor label for log messages.
     *
     * @param principal authenticated principal, when available.
     * @return actor label derived from the principal or request ID.
     */
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
