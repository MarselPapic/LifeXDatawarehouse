package at.htlle.freq.infrastructure.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackRoutingIntegrationTest {

    @Test
    void logbackConfigurationRoutesLoggersToExpectedAppenders() throws Exception {
        LoggerContext context = new LoggerContext();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(Objects.requireNonNull(
                    getClass().getClassLoader().getResource("logback-spring.xml")));

            Logger webLogger = context.getLogger("at.htlle.freq.web");
            Logger auditLogger = context.getLogger("at.htlle.freq.audit");
            Logger opsLogger = context.getLogger("at.htlle.freq.infrastructure.lucene");
            Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

            assertThat(webLogger.isAdditive()).isFalse();
            assertThat(auditLogger.isAdditive()).isFalse();
            assertThat(opsLogger.isAdditive()).isFalse();

            assertThat(appenderNames(webLogger)).containsExactly("FILE_APP");
            assertThat(appenderNames(auditLogger)).containsExactly("FILE_AUDIT");
            assertThat(appenderNames(opsLogger)).containsExactly("FILE_OPS");
            assertThat(appenderNames(rootLogger)).containsExactly("FILE_APP");

            FileAppender<?> appAppender = (FileAppender<?>) webLogger.getAppender("FILE_APP");
            FileAppender<?> auditAppender = (FileAppender<?>) auditLogger.getAppender("FILE_AUDIT");
            FileAppender<?> opsAppender = (FileAppender<?>) opsLogger.getAppender("FILE_OPS");

            assertThat(appAppender.getFile()).endsWith("LiveXDataWarehouse-app.log");
            assertThat(auditAppender.getFile()).endsWith("LiveXDataWarehouse-audit.log");
            assertThat(opsAppender.getFile()).endsWith("LiveXDataWarehouse-ops.log");
        } finally {
            context.stop();
        }
    }

    private List<String> appenderNames(Logger logger) {
        List<String> names = new ArrayList<>();
        Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> iterator = logger.iteratorForAppenders();
        while (iterator.hasNext()) {
            names.add(iterator.next().getName());
        }
        return names;
    }
}
