package org.nmsi.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.net.URI
import javax.sql.DataSource

object DatabaseFactory {
    fun create(
        databaseUrl: String? = System.getenv("DATABASE_URL"),
        databaseUser: String? = System.getenv("DATABASE_USER"),
        databasePassword: String? = System.getenv("DATABASE_PASSWORD"),
    ): HikariDataSource {
        val connection = databaseUrl?.takeIf { it.isNotBlank() }?.let(::parseDatabaseUrl)
        val config =
            HikariConfig().apply {
                jdbcUrl = connection?.jdbcUrl ?: "jdbc:h2:file:./data/nmsi;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
                username = connection?.username ?: databaseUser ?: "sa"
                password = connection?.password ?: databasePassword ?: ""
                maximumPoolSize = (System.getenv("DATABASE_POOL_SIZE") ?: "5").toInt()
                minimumIdle = 1
                isAutoCommit = true
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
            }
        val dataSource = HikariDataSource(config)
        migrate(dataSource)
        return dataSource
    }

    private fun migrate(dataSource: DataSource) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    private fun parseDatabaseUrl(value: String): DatabaseConnection {
        if (value.startsWith("jdbc:")) {
            return DatabaseConnection(value, null, null)
        }
        val uri = URI(value)
        require(uri.scheme == "postgres" || uri.scheme == "postgresql") {
            "DATABASE_URL must be a JDBC URL or a PostgreSQL URL"
        }
        val credentials = uri.userInfo?.split(":", limit = 2).orEmpty()
        val port = if (uri.port == -1) 5432 else uri.port
        val jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.path}${uri.query?.let { "?$it" }.orEmpty()}"
        return DatabaseConnection(
            jdbcUrl = jdbcUrl,
            username = credentials.getOrNull(0),
            password = credentials.getOrNull(1),
        )
    }

    private data class DatabaseConnection(
        val jdbcUrl: String,
        val username: String?,
        val password: String?,
    )
}

