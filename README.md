# Student Support Navigator

An evidence-linked full-stack prototype for the North-western Medical and Science Institute (NMSI).

The project asks:

> How do university students choose a support source when campus support categories are unclear or overlapping, and what information helps them make that choice?

Students can describe a problem in their own words, search and filter a database-backed support directory, compare overlapping services, inspect scope and access details, book or cancel an appointment, and ask a DeepSeek V4 Flash support guide for immediate routing help.

This is a research prototype. It does not provide medical diagnosis, emergency response, or production authentication.

## Research-to-design direction

The interface follows five simulated-pilot themes from the project research plan:

1. Students start with familiar people or their own vocabulary when official labels are unclear (T01–T03).
2. Overlapping ownership causes hesitation, redirection, and repeated explanations (T04–T07).
3. Response time, channel, availability, and distance break ties between plausible options (T08–T11).
4. Scope examples, provider role, eligibility, preparation, and privacy clarify category boundaries (T12–T15, T19).
5. Confirmation, appointment status, cancellation, and a fallback route reduce the cost of an uncertain choice (T16–T18).

The feature-to-evidence mapping and usability plan are documented in [Research and UX rationale](docs/research-and-ux-rationale.md).

## What is implemented

- Server-rendered, responsive Pebble pages with a consistent portal layout
- Persistent large-font accessibility preference
- Screen-reader labels, landmarks, live regions, keyboard focus management, and AA contrast
- Support directory with ten need categories and twelve facilities
- Own-language search and category filtering across facility text and taxonomy keywords
- Facility detail pages showing provider, scope, distance, response time, access mode, eligibility, preparation, and rating
- Database-backed appointment availability, booking, listing, and cancellation
- Accessible support chat available from the lower-right corner
- DeepSeek V4 Flash integration with server-side, user-scoped tools for search and appointment actions
- Deterministic database-guided fallback when no AI key is configured
- Flyway migrations for H2 in local development and PostgreSQL in deployment
- CI for ktlint, detekt, tests, and Playwright/axe accessibility checks

## Architecture

```text
Browser
  ├─ Pebble pages + CSS + accessible JavaScript
  └─ Support Guide chat
        ↓
Ktor 3.5 / Kotlin 2.2 / JDK 21
  ├─ Portal and appointment routes
  ├─ DeepSeek V4 Flash assistant + scoped tools
  └─ SupportRepository
        ↓
H2 locally / PostgreSQL in deployment
```

See [System architecture](docs/architecture.md) and [Database ERD](docs/database-erd.md).

## Requirements

- JDK 21
- Git
- Node.js 20 or later only for accessibility checks
- Docker only if building the deployment image locally

The repository includes the Gradle wrapper, so a system Gradle installation is not required.

### Install and select Java 21

macOS with Homebrew:

```bash
brew install --cask temurin@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version
```

If Homebrew installs `openjdk@21` instead:

```bash
brew install openjdk@21
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Ubuntu/Debian:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
sudo update-alternatives --config java
java -version
```

Windows with winget:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
java -version
```

If Gradle reports a Java-version error, set `JAVA_HOME` to the JDK 21 installation before rerunning `./gradlew`.

## Run locally

```bash
git clone https://github.com/User-Experience-Research/User-Experience-Research-Prototype.git
cd User-Experience-Research-Prototype
chmod +x ./gradlew
./gradlew run
```

Open [http://localhost:8080/login](http://localhost:8080/login). The prototype login accepts blank fields and opens the seeded demonstration account.

Local data is stored in `./data/nmsi.mv.db`. Flyway creates and seeds the schema automatically.

## Configure DeepSeek V4 Flash

Do not put the API key in source code, a committed file, browser JavaScript, or the database.

For a local shell:

```bash
export DEEPSEEK_API_KEY="your-key-here"
export DEEPSEEK_MODEL="deepseek-v4-flash"
./gradlew run
```

The key belongs in the server process environment. In the deployment dashboard, add it as the secret environment variable `DEEPSEEK_API_KEY`. The supplied deployment blueprint deliberately leaves this value for the repository owner to enter.

Optional variables:

```bash
export DEEPSEEK_BASE_URL="https://api.deepseek.com/v1"
export DEEPSEEK_MODEL="deepseek-v4-flash"
```

Copy [.env.example](.env.example) as a reference, but note that the application does not automatically load `.env`; export the variables or configure them in the hosting platform.

Without `DEEPSEEK_API_KEY`, the chat still works in `database-guided fallback` mode. It can search the database and explain likely options, while booking and cancellation remain available through the rendered pages.

## AI safety and scope

The English system prompt is stored at [support-assistant-system.txt](src/main/resources/prompts/support-assistant-system.txt). It instructs the assistant to:

- ask short clarifying questions when the category is uncertain;
- ground recommendations in current database records;
- explain overlap using scope, provider, eligibility, access, and response information;
- avoid medical or mental-health diagnosis;
- show urgent safety guidance instead of continuing ordinary routing;
- request explicit confirmation before booking or cancelling;
- use only the current authenticated user's appointment records.

The model never receives database credentials. Tool calls are executed server-side, and user identity is injected by the application rather than accepted from model arguments.

## Database configuration

The default local database is H2 in PostgreSQL compatibility mode.

For PostgreSQL, provide either a standard PostgreSQL URL:

```bash
export DATABASE_URL="postgresql://user:password@host:5432/nmsi"
```

or a JDBC URL with separate credentials:

```bash
export DATABASE_URL="jdbc:postgresql://host:5432/nmsi"
export DATABASE_USER="user"
export DATABASE_PASSWORD="password"
```

Flyway migrations are in `src/main/resources/db/migration`.

## Quality checks

Kotlin formatting, static analysis, and tests:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew test
```

Accessibility checks:

```bash
npm ci
npx playwright install chromium
./gradlew run
```

In another terminal:

```bash
npm run a11y:axe
```

The axe script logs in to the prototype and checks Login, Dashboard, Support Search, Facility Detail, and Appointments against WCAG A/AA tags. GitHub Actions runs the complete sequence on every push and pull request to `main`.

## Build

Standard application:

```bash
./gradlew clean build
```

Runnable fat JAR:

```bash
./gradlew buildFatJar
java -jar build/libs/nmsi-support-navigator-all.jar
```

Docker:

```bash
docker build -t nmsi-support-navigator .
docker run --rm -p 8080:8080 \
  -e DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
  nmsi-support-navigator
```

## Deploy the full stack

The repository includes a [Render Blueprint](render.yaml) and [Dockerfile](Dockerfile). The Blueprint creates:

- a free Docker web service in Singapore;
- a free PostgreSQL database;
- an internal database connection;
- a generated session secret;
- HTTPS-only session cookies;
- `/health` application checks;
- automatic deploys after GitHub CI succeeds.

Create the Blueprint from this repository, then add `DEEPSEEK_API_KEY` under:

**Render Dashboard → nmsi-support-navigator → Environment → Add Environment Variable**

The application deploys without the key in database-guided fallback mode, so the secret can be added later. A free Render web service may spin down after inactivity, and a free Render PostgreSQL database expires after 30 days; use a paid datastore before treating the prototype as persistent.

## Documentation

- [Research and UX rationale](docs/research-and-ux-rationale.md)
- [Wireframes](docs/wireframes/README.md)
- [System architecture](docs/architecture.md)
- [Database ERD](docs/database-erd.md)
- [AI integration](docs/deepseek-integration.md)
- [Usability testing and retrospective](docs/usability-testing.md)

The GitHub Wiki mirrors these pages and records page-specific wireframe changes so that later iterations can be traced to research and issues.

## Privacy and prototype limits

- The login is intentionally non-production and has one seeded demo account.
- Do not enter real medical, safeguarding, financial, immigration, or personally identifying information.
- Conversation and appointment data are stored for prototype behavior only.
- Ratings and service records are seeded research content, not official university claims.
- Automated accessibility checks support, but do not replace, testing with keyboard, screen-reader, large-text, and other access-needs participants.

## License

Course prototype. No production-service warranty is provided.
