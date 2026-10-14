package com.joshua.mangatracker;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("production")
public class ProductionDatabaseConfig {
    @Bean
    DataSource productionDataSource(Environment env) {
        String url = env.getProperty("DATABASE_URL", "");
        if (url.isBlank()) throw new IllegalStateException("Configure DATABASE_URL with a persistent PostgreSQL database before deploying.");
        HikariConfig config = databaseConfig(url, env.getProperty("DATABASE_USERNAME"), env.getProperty("DATABASE_PASSWORD"));
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15000);
        return new HikariDataSource(config);
    }

    // Accept Neon/Vercel connection strings directly; keep credentials out of the JDBC URL and logs.
    static HikariConfig databaseConfig(String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            URI uri = URI.create(url);
            String userInfo = uri.getRawUserInfo();
            if (userInfo != null) {
                String[] credentials = userInfo.split(":", 2);
                username = URLDecoder.decode(credentials[0], StandardCharsets.UTF_8);
                password = credentials.length > 1 ? URLDecoder.decode(credentials[1], StandardCharsets.UTF_8) : "";
            }
            String query = uri.getRawQuery();
            config.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                    + uri.getRawPath() + (query == null ? "?sslmode=require" : "?" + query));
        } else if (url.startsWith("jdbc:postgresql://")) config.setJdbcUrl(url);
        else throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL connection string.");
        if (username == null || username.isBlank() || password == null)
            throw new IllegalArgumentException("PostgreSQL username and password are required.");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        return config;
    }
}
