package com.joshua.mangatracker;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductionDatabaseConfigTests {
    @Test void acceptsNeonUrlsAndSeparatesEncodedCredentials() {
        var config = ProductionDatabaseConfig.databaseConfig("postgresql://reader:demo%2Bpassword@db.example/app?sslmode=require", null, null);
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://db.example/app?sslmode=require");
        assertThat(config.getUsername()).isEqualTo("reader");
        assertThat(config.getPassword()).isEqualTo("demo+password");
    }
    @Test void acceptsJdbcUrlsAndRejectsEphemeralStorageInProduction() {
        assertThat(ProductionDatabaseConfig.databaseConfig("jdbc:postgresql://db.example/app", "reader", "demo").getUsername()).isEqualTo("reader");
        assertThatThrownBy(() -> ProductionDatabaseConfig.databaseConfig("jdbc:h2:file:./data/test", "reader", "demo"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
