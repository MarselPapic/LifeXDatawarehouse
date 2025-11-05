package at.htlle.freq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt der LifeX-Datawarehouse-Anwendung.
 * <p>
 * Aktiviert standardmäßig das {@code default}-Spring-Profil sowie die Camel-Routen
 * {@link at.htlle.freq.infrastructure.camel.UnifiedIndexingRoutes} und den
 * {@link at.htlle.freq.infrastructure.camel.LuceneIndexingHubRoute}, die gemeinsam
 * mit dem {@link at.htlle.freq.infrastructure.lucene.LuceneIndexService LuceneIndexService}
 * den Suchindex pflegen. Konfigurationen (z.&nbsp;B. Datenquelle, Lucene-Flags und
 * Profile) werden zentral in {@code application.yml} verwaltet.
 * </p>
 */
@SpringBootApplication
public class LifeXDatawrehouseAppApplication {

        /** Startet die Spring-Boot-Anwendung.
         * <p>
         * Erwartet eine erreichbare Datenbank sowie Schreibzugriff auf den Lucene-Indexpfad
         * ({@code target/lifex-index}); bei deaktiviertem Camel-Lucene-Setup (Property
         * {@code lifex.lucene.camel.enabled=false}) müssen Reindex-Aufrufe manuell erfolgen.
         * </p>
         */
        public static void main(String[] args) {
                SpringApplication.run(LifeXDatawrehouseAppApplication.class, args);
        }
}
