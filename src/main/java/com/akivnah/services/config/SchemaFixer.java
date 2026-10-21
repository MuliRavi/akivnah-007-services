package com.akivnah.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * An H2 file created by an older version of the app can carry CHECK constraints for enum columns that do not know
 * newer values (for example the EMPLOYEE or HR role). Hibernate's "update" mode never changes them, so saving the new
 * employee's login fails with "Check constraint violation". This drops those old constraints once at start-up.
 * The application itself validates the values, so nothing is lost. Only runs on H2.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaFixer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaFixer.class);
    private final JdbcTemplate jdbc;

    public SchemaFixer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String url = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getURL());
            if (url == null || !url.startsWith("jdbc:h2:")) return;
            List<Map<String, Object>> rows = jdbc.queryForList(
                "select table_name, constraint_name from information_schema.table_constraints "
              + "where constraint_type = 'CHECK' and table_schema = 'PUBLIC'");
            for (Map<String, Object> r : rows) {
                String table = String.valueOf(r.get("TABLE_NAME"));
                String name = String.valueOf(r.get("CONSTRAINT_NAME"));
                if (!table.matches("[A-Za-z0-9_]+") || !name.matches("[A-Za-z0-9_]+")) continue;
                jdbc.execute("alter table \"" + table + "\" drop constraint \"" + name + "\"");
                log.info("Dropped old CHECK constraint {} on {}", name, table);
            }
        } catch (RuntimeException e) {
            log.warn("Schema check skipped: {}", e.getMessage());
        }
    }
}
