package com.hostlify;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Ensures the target PostgreSQL database exists before Spring Boot wires up the
 * DataSource / Flyway. PostgreSQL has no "createDatabaseIfNotExist" JDBC flag, so
 * we connect to the maintenance "postgres" database and create the target if missing.
 *
 * <p>Runs only for local (non-prod) profiles; any failure is logged and ignored so it
 * never blocks startup against a managed/remote database.
 */
final class DatabaseBootstrap {

    private DatabaseBootstrap() {}

    static void ensureDatabaseExists() {
        String activeProfile = resolve("SPRING_PROFILES_ACTIVE", "spring.profiles.active", "dev");
        if ("prod".equalsIgnoreCase(activeProfile)) {
            return; // managed DB in production – do not auto-create
        }

        String host = resolve("DB_HOST", "db.host", "localhost");
        String port = resolve("DB_PORT", "db.port", "5432");
        String dbName = resolve("DB_NAME", "db.name", "hostlify-db");
        String user = resolve("DB_USER", "db.user", "postgres");
        String pass = resolve("DB_PASS", "db.pass", "postgres");

        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";

        try (Connection conn = DriverManager.getConnection(adminUrl, user, pass);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + dbName.replace("'", "''") + "'")) {
                if (rs.next()) {
                    return; // already exists
                }
            }
            // Identifier may contain a hyphen (hostlify-db) -> must be double-quoted.
            stmt.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
            System.out.println("[BOOTSTRAP] Created PostgreSQL database \"" + dbName + "\"");
        } catch (Exception e) {
            System.err.println("[BOOTSTRAP] Could not auto-create database (continuing): " + e.getMessage());
        }
    }

    private static String resolve(String env, String sysProp, String fallback) {
        String v = System.getenv(env);
        if (v == null || v.isBlank()) {
            v = System.getProperty(sysProp);
        }
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
