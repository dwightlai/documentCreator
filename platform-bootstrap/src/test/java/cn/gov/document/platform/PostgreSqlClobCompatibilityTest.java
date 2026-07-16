package cn.gov.document.platform;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class PostgreSqlClobCompatibilityTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine")
            .withDatabaseName("document_platform")
            .withUsername("platform")
            .withPassword("platform-test-only");

    @Test
    void allMigrationsRunAndLogicalClobColumnsUsePortableText() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(22);

        try (Connection connection = POSTGRES.createConnection("")) {
            assertThat(databaseSpecificJsonColumnCount(connection)).isZero();
            assertTextColumns(connection, "ai_document_type_config",
                    Set.of("applicable_scenario", "required_fields", "recommended_structure",
                            "forbidden_patterns", "closing_patterns"));
            assertTextColumns(connection, "ai_prompt_template",
                    Set.of("applicable_models", "system_role", "input_fields", "model_parameters"));
            verifyLongClobRoundTrip(connection);
        }
    }

    private long databaseSpecificJsonColumnCount(Connection connection) throws Exception {
        String sql = """
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND (data_type IN ('json', 'jsonb') OR udt_name IN ('json', 'jsonb'))
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private void assertTextColumns(Connection connection, String table, Set<String> columns) throws Exception {
        String sql = """
                SELECT column_name, data_type
                  FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                int matches = 0;
                while (result.next()) {
                    if (columns.contains(result.getString("column_name"))) {
                        assertThat(result.getString("data_type")).isEqualTo("text");
                        matches++;
                    }
                }
                assertThat(matches).isEqualTo(columns.size());
            }
        }
    }

    private void verifyLongClobRoundTrip(Connection connection) throws Exception {
        String longJson = "{\"sections\":[\"" + "政务材料".repeat(30_000) + "\"]}";
        String insert = """
                INSERT INTO ai_document_type_config
                    (id, tenant_id, type_code, type_name, document_category,
                     recommended_structure, current_version, lifecycle_status,
                     created_at, updated_at, created_by, updated_by, is_deleted, lock_version)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, FALSE, 0)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, "clob-compatibility-test");
            statement.setString(2, "tenant-test");
            statement.setString(3, "TEST-CLOB");
            statement.setString(4, "CLOB compatibility");
            statement.setString(5, "事务文书");
            statement.setString(6, longJson);
            statement.setString(7, "草稿");
            statement.setString(8, "ci");
            statement.setString(9, "ci");
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT recommended_structure FROM ai_document_type_config WHERE id = ?")) {
            statement.setString(1, "clob-compatibility-test");
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(longJson);
            }
        }
    }
}
