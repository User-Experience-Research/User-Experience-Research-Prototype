package org.nmsi.data

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
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

    fun availableSlots(facilityId: Long): List<AppointmentSlot> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, facility_id, starts_at
                    FROM appointment_slots
                    WHERE facility_id = ? AND is_available = TRUE AND starts_at > CURRENT_TIMESTAMP
                    ORDER BY starts_at ASC
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, facilityId)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    AppointmentSlot(
                                        id = result.getLong("id"),
                                        facilityId = result.getLong("facility_id"),
                                        startsAt = result.getObject("starts_at", OffsetDateTime::class.java),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    fun ensureFutureSlots(
        now: OffsetDateTime = OffsetDateTime.now(INSTITUTE_ZONE),
        numberOfDays: Int = 14,
    ) {
        val localNow = now.atZoneSameInstant(INSTITUTE_ZONE)
        val candidateTimes =
            buildList {
                repeat(numberOfDays) { dayOffset ->
                    val date = localNow.toLocalDate().plusDays(dayOffset.toLong())
                    STANDARD_SLOT_TIMES.forEach { time ->
                        val candidate = date.atTime(time).atZone(INSTITUTE_ZONE).toOffsetDateTime()
                        if (candidate.isAfter(now)) add(candidate)
                    }
                }
            }

        dataSource.connection.use { connection ->
            val facilityIds =
                connection
                    .prepareStatement("SELECT id FROM facilities ORDER BY id")
                    .use { statement ->
                        statement.executeQuery().use { result ->
                            buildList {
                                while (result.next()) add(result.getLong("id"))
                            }
                        }
                    }

            connection.autoCommit = false
            try {
                connection
                    .prepareStatement(
                        "SELECT COUNT(*) FROM appointment_slots WHERE facility_id = ? AND starts_at = ?",
                    ).use { exists ->
                        connection
                            .prepareStatement(
                                "INSERT INTO appointment_slots (facility_id, starts_at, is_available) VALUES (?, ?, TRUE)",
                            ).use { insert ->
                                facilityIds.forEach { facilityId ->
                                    candidateTimes.forEach { startsAt ->
                                        exists.setLong(1, facilityId)
                                        exists.setObject(2, startsAt)
                                        val alreadyExists =
                                            exists.executeQuery().use { result ->
                                                result.next()
                                                result.getInt(1) > 0
                                            }
                                        if (!alreadyExists) {
                                            insert.setLong(1, facilityId)
                                            insert.setObject(2, startsAt)
                                            insert.addBatch()
                                        }
                                    }
                                }
                                insert.executeBatch()
                            }
                    }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
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
            connection.autoCommit = false
            try {
                val slotId =
                    connection
                        .prepareStatement(
                            """
                            SELECT id
                            FROM appointment_slots
                            WHERE facility_id = ?
                              AND starts_at = ?
                              AND is_available = TRUE
                              AND starts_at > CURRENT_TIMESTAMP
                            FOR UPDATE
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setLong(1, facilityId)
                            statement.setObject(2, startsAt)
                            statement.executeQuery().use { result ->
                                check(result.next()) { "The requested appointment slot is not available" }
                                result.getLong(1)
                            }
                        }
                connection
                    .prepareStatement(
                        "UPDATE appointment_slots SET is_available = FALSE WHERE id = ?",
                    ).use { statement ->
                        statement.setLong(1, slotId)
                        statement.executeUpdate()
                    }
                val appointmentId =
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
                                keys.getLong(1)
                            }
                        }
                val appointment =
                    appointmentById(connection, userId, appointmentId)
                        ?: error("Appointment was not found after insert")
                connection.commit()
                return appointment
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
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
                val existing = appointmentById(connection, userId, appointmentId)
                if (existing == null || existing.status != "BOOKED") {
                    connection.rollback()
                    return@use null
                }
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
                if (updated == 1) {
                    connection
                        .prepareStatement(
                            """
                            UPDATE appointment_slots
                            SET is_available = TRUE
                            WHERE facility_id = ? AND starts_at = ?
                            """.trimIndent(),
                        ).use { statement ->
                            statement.setLong(1, existing.facilityId)
                            statement.setObject(2, existing.startsAt)
                            statement.executeUpdate()
                        }
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

    fun appointmentById(
        userId: Long,
        appointmentId: Long,
    ): Appointment? =
        dataSource.connection.use { connection ->
            appointmentById(connection, userId, appointmentId)
        }

    fun recentConversation(
        userId: Long,
        limit: Int = 12,
    ): List<ConversationMessage> =
        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT cm.role, cm.content
                    FROM conversation_messages cm
                    JOIN conversations c ON c.id = cm.conversation_id
                    WHERE c.user_id = ?
                    ORDER BY cm.created_at DESC, cm.id DESC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, userId)
                    statement.setInt(2, limit.coerceIn(1, 30))
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    ConversationMessage(
                                        role = result.getString("role"),
                                        content = result.getString("content"),
                                    ),
                                )
                            }
                        }.reversed()
                    }
                }
        }

    fun appendConversationMessage(
        userId: Long,
        role: String,
        content: String,
    ) {
        require(role in setOf("USER", "ASSISTANT", "TOOL"))
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val conversationId = activeConversationId(connection, userId)
                connection
                    .prepareStatement(
                        """
                        INSERT INTO conversation_messages (conversation_id, role, content)
                        VALUES (?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, conversationId)
                        statement.setString(2, role)
                        statement.setString(3, content.take(6000))
                        statement.executeUpdate()
                    }
                connection
                    .prepareStatement(
                        "UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    ).use { statement ->
                        statement.setLong(1, conversationId)
                        statement.executeUpdate()
                    }
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun activeConversationId(
        connection: Connection,
        userId: Long,
    ): Long {
        connection
            .prepareStatement(
                """
                SELECT id
                FROM conversations
                WHERE user_id = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeQuery().use { result ->
                    if (result.next()) return result.getLong(1)
                }
            }
        connection
            .prepareStatement(
                "INSERT INTO conversations (user_id) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setLong(1, userId)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "Conversation id was not generated" }
                    return keys.getLong(1)
                }
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

    private companion object {
        val INSTITUTE_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val STANDARD_SLOT_TIMES: List<LocalTime> = listOf(LocalTime.of(10, 0), LocalTime.of(14, 30))
    }
}
