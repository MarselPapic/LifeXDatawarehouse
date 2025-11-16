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

        taskExecutor.execute(task);
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
