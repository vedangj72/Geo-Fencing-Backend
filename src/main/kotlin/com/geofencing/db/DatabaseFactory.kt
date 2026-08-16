package com.geofencing.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import org.slf4j.LoggerFactory
import java.sql.Connection

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    fun init() {
        val dotenv = dotenv {
            ignoreIfMissing = true
        }

        val dbUrl = dotenv["SUPABASE_DB_URL"] 
            ?: System.getenv("SUPABASE_DB_URL") 
            ?: "postgresql://postgres:GeoFencing$1763@db.koigsgmvkvsmrgrvmfku.supabase.co:5432/postgres"

        val dbUser = dotenv["SUPABASE_DB_USER"] 
            ?: System.getenv("SUPABASE_DB_USER") 
            ?: "postgres"

        val dbPassword = dotenv["SUPABASE_DB_PASSWORD"] 
            ?: System.getenv("SUPABASE_DB_PASSWORD") 
            ?: "GeoFencing$1763"

        System.setProperty("java.net.preferIPv4Stack", "true")
        System.setProperty("java.net.preferIPv4Addresses", "true")

        val cleanUrl = when {
            dbUrl.contains("@") -> "jdbc:postgresql://" + dbUrl.substringAfter("@")
            !dbUrl.startsWith("jdbc:") -> "jdbc:$dbUrl"
            else -> dbUrl
        }

        logger.info("Initializing HikariCP Database Pool for Supabase PostgreSQL...")

        val config = HikariConfig().apply {
            jdbcUrl = cleanUrl
            username = dbUser
            password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            idleTimeout = 300000
            connectionTimeout = 20000
            initializationFailTimeout = -1L
            isAutoCommit = true
        }

        dataSource = HikariDataSource(config)
        logger.info("Database Pool initialized successfully.")
    }

    fun getConnection(): Connection = dataSource.connection
}
