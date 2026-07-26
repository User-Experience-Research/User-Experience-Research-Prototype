# Database entity-relationship diagram

The schema supports overlapping categories through a many-to-many relationship between facilities and categories. Appointment slots are separate from appointments so availability can be changed transactionally. Conversations are linked to the current demo user, not to a client-supplied identity.

```mermaid
erDiagram
    USERS ||--o{ APPOINTMENTS : books
    FACILITIES ||--o{ APPOINTMENTS : receives
    FACILITIES ||--o{ APPOINTMENT_SLOTS : offers
    FACILITIES ||--o{ FACILITY_CATEGORIES : classified_as
    CATEGORIES ||--o{ FACILITY_CATEGORIES : includes
    USERS ||--o{ CONVERSATIONS : owns
    CONVERSATIONS ||--o{ CONVERSATION_MESSAGES : contains

    USERS {
        bigint id PK
        varchar student_id UK
        varchar display_name
        varchar email
    }
    CATEGORIES {
        bigint id PK
        varchar slug UK
        varchar name
        text description
        text keywords
    }
    FACILITIES {
        bigint id PK
        varchar slug UK
        varchar name
        text summary
        varchar provider
        varchar location
        int distance_minutes
        decimal rating
        varchar response_time
        varchar contact_mode
        text eligibility
        text preparation
        text tags
    }
    FACILITY_CATEGORIES {
        bigint facility_id PK,FK
        bigint category_id PK,FK
    }
    APPOINTMENT_SLOTS {
        bigint id PK
        bigint facility_id FK
        timestamp starts_at
        boolean is_available
    }
    APPOINTMENTS {
        bigint id PK
        bigint user_id FK
        bigint facility_id FK
        timestamp starts_at
        varchar status
        text note
        timestamp created_at
        timestamp updated_at
    }
    CONVERSATIONS {
        bigint id PK
        bigint user_id FK
        timestamp created_at
        timestamp updated_at
    }
    CONVERSATION_MESSAGES {
        bigint id PK
        bigint conversation_id FK
        varchar role
        text content
        timestamp created_at
    }
```

## Why this structure fits the research

- `FACILITY_CATEGORIES` allows one service to belong to several need areas, representing the overlap seen in T04–T07 rather than forcing one false category.
- searchable category `keywords` let students use their own vocabulary, supporting T01–T03.
- facility scope and access fields hold the comparison details requested in T08–T15 and T19.
- slots and appointment status support confirmation, review, and cancellation in T16–T18.
- conversation history lets the guide retain the student's current decision context without exposing unrelated users.
