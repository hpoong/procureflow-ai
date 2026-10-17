package com.hopoong.procureflow.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** IT 공용 PostgreSQL. 컨텍스트가 만들어질 때 한 번만 기동해 테스트 클래스 간에 공유한다. */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresContainerSupport {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("procureflow")
            .withUsername("procureflow")
            .withPassword("procureflow");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        synchronized (POSTGRES) {
            if (!POSTGRES.isRunning()) {
                POSTGRES.start();
            }
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
