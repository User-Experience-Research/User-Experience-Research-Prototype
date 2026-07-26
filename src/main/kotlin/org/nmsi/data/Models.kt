package org.nmsi.data

import java.time.OffsetDateTime

data class Category(
    val id: Long,
    val slug: String,
    val name: String,
    val description: String,
    val keywords: String,
)

data class Facility(
    val id: Long,
    val slug: String,
    val name: String,
    val summary: String,
    val provider: String,
    val location: String,
    val distanceMinutes: Int,
    val rating: Double,
    val responseTime: String,
    val contactMode: String,
    val eligibility: String,
    val preparation: String,
    val tags: String,
    val categories: List<Category>,
)

data class Appointment(
    val id: Long,
    val userId: Long,
    val facilityId: Long,
    val facilityName: String,
    val startsAt: OffsetDateTime,
    val status: String,
    val note: String?,
)

data class UserAccount(
    val id: Long,
    val studentId: String,
    val displayName: String,
    val email: String,
)

