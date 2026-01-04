package at.htlle.freq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the LifeX Data Warehouse application.
 * <p>
 * Enables the {@code default} Spring profile and wires the Camel routes
 * {@link at.htlle.freq.infrastructure.camel.UnifiedIndexingRoutes} and
 * {@link at.htlle.freq.infrastructure.camel.LuceneIndexingHubRoute}. Together with the
 * {@link at.htlle.freq.infrastructure.lucene.LuceneIndexService LuceneIndexService} they
 * maintain the search index. Configuration (for example, data sources, Lucene flags, and
 * active profiles) is managed centrally in {@code application.yml}.
 * </p>
 */
@SpringBootApplication
public class LifeXDatawarehouseAppApplication {

        /** Launches the Spring Boot application.
         * <p>
         * Requires a reachable database and write access to the Lucene index path
         * ({@code target/lifex-index}); when the Camel/Lucene integration is disabled
         * (property {@code lifex.lucene.camel.enabled=false}) reindex calls must be triggered manually.
         * </p>
         */
        /**
         * Executes the main operation.
         * @param args args.
         */
        public static void main(String[] args) {
                SpringApplication.run(LifeXDatawarehouseAppApplication.class, args);
        }
}
