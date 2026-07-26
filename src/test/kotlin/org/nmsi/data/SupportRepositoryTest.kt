package org.nmsi.data

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SupportRepositoryTest {
    @Test
    fun `Neon channel binding parameter is normalized for pgJDBC`() {
        val query = "sslmode=require&channel_binding=require"

        assertEquals(
            "sslmode=require&channelBinding=require",
            DatabaseFactory.normalizePostgresQuery(query),
        )
    }

    @Test
    fun `seeded taxonomy is searchable by a student's own words`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)

            assertEquals("John doe", repository.demoUser().displayName)
            assertEquals(10, repository.listCategories().size)
            assertEquals(12, repository.searchFacilities(null, null).size)

            val results = repository.searchFacilities("deadline", null)
            assertTrue(results.isNotEmpty())
            assertTrue(results.any { facility -> facility.categories.any { it.slug == "academic-study" } })
        }
    }

    @Test
    fun `appointments can be booked and cancelled for the current user`() {
        database().use { dataSource ->
            val repository = SupportRepository(dataSource)
            val user = repository.demoUser()
            val facility = repository.searchFacilities(null, null).first { repository.availableSlots(it.id).isNotEmpty() }
            val slot = repository.availableSlots(facility.id).first()

            val booked =
                repository.bookAppointment(
                    userId = user.id,
                    facilityId = facility.id,
                    startsAt = slot.startsAt,
                    note = "I would like to understand which service fits my situation.",
                )

            assertEquals("BOOKED", booked.status)
            assertTrue(repository.availableSlots(facility.id).none { it.id == slot.id })

            val cancelled = assertNotNull(repository.cancelAppointment(user.id, booked.id))
            assertEquals("CANCELLED", cancelled.status)
            assertTrue(repository.availableSlots(facility.id).any { it.id == slot.id })
        }
    }

    private fun database() =
        DatabaseFactory.create(
            databaseUrl =
                "jdbc:h2:mem:test-${UUID.randomUUID()};" +
                    "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        )
}
