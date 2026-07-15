package com.mteam.rebuildengine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Configuration
public class DatabaseCheckConfig {

    @Bean
    public CommandLineRunner checkDatabase(DataSource dataSource, Environment env) {
        return args -> {

            try (Connection conn = dataSource.getConnection()) {

                log.info("====================================");
                log.info("Database Connection Check Start");

                log.info("Username : {}", env.getProperty("SPRING_DATASOURCE_USERNAME"));
                log.info("Profile  : {}", String.join(",", env.getActiveProfiles()));
                log.info("JDBC URL : {}", conn.getMetaData().getURL());
                log.info("User     : {}", conn.getMetaData().getUserName());

                try (Statement stmt = conn.createStatement()) {

                    ResultSet rs = stmt.executeQuery(
                            "select current_database(), current_schema()"
                    );

                    if (rs.next()) {
                        log.info("Database : {}", rs.getString(1));
                        log.info("Schema   : {}", rs.getString(2));
                    }

                    rs = stmt.executeQuery(
                            "select count(*) from test_property"
                    );

                    if (rs.next()) {
                        log.info("Rows     : {}", rs.getInt(1));
                    }
                }

                log.info("Database Connection Check Success");
                log.info("====================================");

            } catch (Exception e) {
                log.error("Database Connection Check Failed", e);
            }
        };
    }
}