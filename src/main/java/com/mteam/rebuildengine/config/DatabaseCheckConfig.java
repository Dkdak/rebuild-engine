package com.mteam.rebuildengine.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Configuration
public class DatabaseCheckConfig {

    @Bean
    public CommandLineRunner checkDatabase(DataSource dataSource, Environment env) {
        return args -> {

            try (Connection conn = dataSource.getConnection()) {

                System.out.println("====================================");
                System.out.println("Username : " + env.getProperty("SPRING_DATASOURCE_USERNAME"));
                System.out.println("Password : " + env.getProperty("SPRING_DATASOURCE_PASSWORD"));
                System.out.println("Profile  : " + String.join(",", env.getActiveProfiles()));
                System.out.println("JDBC URL : " + conn.getMetaData().getURL());
                System.out.println("User     : " + conn.getMetaData().getUserName());

                Statement stmt = conn.createStatement();

                ResultSet rs = stmt.executeQuery(
                        "select current_database(), current_schema()");

                if (rs.next()) {
                    System.out.println("Database : " + rs.getString(1));
                    System.out.println("Schema   : " + rs.getString(2));
                }

                rs = stmt.executeQuery("select count(*) from test_property");

                if (rs.next()) {
                    System.out.println("Rows     : " + rs.getInt(1));
                }

                System.out.println("====================================");

            }
        };
    }
}