package com.caltdeepl.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 起動時に Flyway のマイグレーションが適用されることを確かめる。
 *
 * <p>Spring Boot 4 では Flyway の自動設定が spring-boot-flyway に分かれており、flyway-core だけでは
 * マイグレーションが走らない。依存の欠落で再発しないよう、実 DB で検査する。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("起動時に V1 が適用され、flyway_schema_history に成功として記録される")
    void v1IsAppliedOnStartup() {
        Boolean success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("V1 で users テーブルが作成されている")
    void usersTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
