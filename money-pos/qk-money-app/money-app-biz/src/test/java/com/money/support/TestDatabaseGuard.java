package com.money.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class TestDatabaseGuard {

    private final String databaseName;
    private final String datasourceUrl;

    public TestDatabaseGuard(@Value("${money.test.database-name}") String databaseName,
                             @Value("${spring.datasource.url}") String datasourceUrl) {
        this.databaseName = databaseName;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void verifyTestDatabase() {
        if (databaseName == null || !databaseName.endsWith("_test")
                || datasourceUrl == null || !datasourceUrl.contains("/" + databaseName)) {
            throw new IllegalStateException("Test datasource must target MONEY_TEST_DB_NAME ending in _test");
        }
    }
}
