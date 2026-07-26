package org.nmsi.data

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.time.OffsetDateTime
import javax.sql.DataSource

class SupportRepository(
    private val dataSource: DataSource,
) {
    fun demoUser(): UserAccount =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, student_id, display_name, email
                    FROM users
                    WHERE student_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, "nmsi-demo")
                    statement.executeQuery().use { result ->
                        check(result.next()) { "Demo user was not seeded" }
                        result.toUser()
                    }
                }
        }

    fun listCategories(): List<Category> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT id, slug, name, description, keywords FROM categories ORDER BY name",
                ).use { statement ->
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) add(result.toCategory())
                        }
                    }
                }
        }

    fun searchFacilities(
        query: String?,
        categorySlug: String?,
    ): List<Facility> =
        dataSource.connection.use { connection ->
            val normalizedQuery = query?.trim()?.lowercase().orEmpty()
            val normalizedCategory = categorySlug?.trim()?.lowercase().orEmpty()
            connection
                .prepareStatement(
                    """
                    SELECT DISTINCT f.id, f.slug, f.name, f.summary, f.provider, f.location,
                           f.distance_minutes, f.rating, f.response_time, f.contact_mode,
                           f.eligibility, f.preparation, f.tags
                    FROM facilities f
                    LEFT JOIN facility_categories fc ON fc.facility_id = f.id
                    LEFT JOIN categories c ON c.id = fc.category_id
                    WHERE (
                        ? = ''
                        OR LOWER(f.name) LIKE ?
                        OR LOWER(f.summary) LIKE ?
                        OR LOWER(f.provider) LIKE ?
                        OR LOWER(f.tags) LIKE ?
                        OR LOWER(c.name) LIKE ?
                        OR LOWER(c.keywords) LIKE ?
                    )
                    AND (? = '' OR LOWER(c.slug) = ?)
                    ORDER BY f.rating DESC, f.distance_minutes ASC, f.name ASC
                    """.trimIndent(),
                ).use { statement ->
                    val like = "%$normalizedQuery%"
                    statement.setString(1, normalizedQuery)
                    (2..7).forEach { statement.setString(it, like) }
                    statement.setString(8, normalizedCategory)
                    statement.setString(9, normalizedCategory)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(result.toFacility(connection))
                            }
                        }
                    }
                }
        }

    fun facilityById(id: Long): Facility? =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, slug, name, summary, provider, location, distance_minutes,
                           rating, response_time, contact_mode, eligibility, preparation, tags
                    FROM facilities
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toFacility(connection) else null
                    }
                }
        }

    fun listAppointments(userId: Long): List<Appointment> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT a.id, a.user_id, a.facility_id, f.name AS facility_name,
                           a.starts_at, a.status, a.note
                    FROM appointments a
                    JOIN facilities f ON f.id = a.facility_id
                    WHERE a.user_id = ?
                    ORDER BY a.starts_at ASC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) add(result.toAppointment())
                        }
                    }
                }
        }

    fun bookAppointment(
        userId: Long,
        facilityId: Long,
        startsAt: OffsetDateTime,
        note: String?,
    ): Appointment {
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO appointments (user_id, facility_id, starts_at, status, note)
                    VALUES (?, ?, ?, 'BOOKED', ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS,
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setLong(2, facilityId)
                    statement.setObject(3, startsAt)
                    statement.setString(4, note?.take(500))
                    statement.executeUpdate()
                    statement.generatedKeys.use { keys ->
                        check(keys.next()) { "Appointment id was not generated" }
                        return appointmentById(connection, userId, keys.getLong(1))
                            ?: error("Appointment was not found after insert")
                    }
                }
        }
    }

    fun cancelAppointment(
        userId: Long,
        appointmentId: Long,
    ): Appointment? =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val updated =
                    connection
                        .prepareStatement(
                            """
                            UPDATE appointments
                            SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND user_id = ? AND status = 'BOOKED'
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setLong(1, appointmentId)
                            statement.setLong(2, userId)
                            statement.executeUpdate()
                        }
                val appointment = if (updated == 1) appointmentById(connection, userId, appointmentId) else null
                connection.commit()
                appointment
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    private fun appointmentById(
        connection: Connection,
        userId: Long,
        appointmentId: Long,
    ): Appointment? =
        connection
            .prepareStatement(
                """
                SELECT a.id, a.user_id, a.facility_id, f.name AS facility_name,
                       a.starts_at, a.status, a.note
                FROM appointments a
                JOIN facilities f ON f.id = a.facility_id
                WHERE a.id = ? AND a.user_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, appointmentId)
                statement.setLong(2, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.toAppointment() else null
                }
            }

    private fun ResultSet.toFacility(connection: Connection): Facility {
        val facilityId = getLong("id")
        return Facility(
            id = facilityId,
            slug = getString("slug"),
            name = getString("name"),
            summary = getString("summary"),
            provider = getString("provider"),
            location = getString("location"),
            distanceMinutes = getInt("distance_minutes"),
            rating = getDouble("rating"),
            responseTime = getString("response_time"),
            contactMode = getString("contact_mode"),
            eligibility = getString("eligibility"),
            preparation = getString("preparation"),
            tags = getString("tags"),
            categories = categoriesForFacility(connection, facilityId),
        )
    }

    private fun categoriesForFacility(
        connection: Connection,
        facilityId: Long,
    ): List<Category> =
        connection
            .prepareStatement(
                """
                SELECT c.id, c.slug, c.name, c.description, c.keywords
                FROM categories c
                JOIN facility_categories fc ON fc.category_id = c.id
                WHERE fc.facility_id = ?
                ORDER BY c.name
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, facilityId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add(result.toCategory())
                    }
                }
            }

    private fun ResultSet.toCategory() =
        Category(
            id = getLong("id"),
            slug = getString("slug"),
            name = getString("name"),
            description = getString("description"),
            keywords = getString("keywords"),
        )

    private fun ResultSet.toUser() =
        UserAccount(
            id = getLong("id"),
            studentId = getString("student_id"),
            displayName = getString("display_name"),
            email = getString("email"),
        )

    private fun ResultSet.toAppointment() =
        Appointment(
            id = getLong("id"),
            userId = getLong("user_id"),
            facilityId = getLong("facility_id"),
            facilityName = getString("facility_name"),
            startsAt = getObject("starts_at", OffsetDateTime::class.java),
            status = getString("status"),
            note = getString("note"),
        )
}

