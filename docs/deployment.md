# Deployment: Render Free Web Service with Neon Free PostgreSQL

**Live service:** [https://nmsi-support-navigator.onrender.com](https://nmsi-support-navigator.onrender.com)

The deployed architecture intentionally separates compute and data:

```mermaid
flowchart LR
    Browser["Student browser"] --> Render["Render Free Web Service<br/>Ktor Docker container"]
    Render --> Neon[("Neon Free PostgreSQL<br/>persistent external project")]
    Render --> DeepSeek["DeepSeek V4 Flash API<br/>optional key"]
    GitHub["GitHub main + CI"] --> Render
```

## Why Neon

Render Free PostgreSQL expires after 30 days. Neon Free is currently $0 with no time limit and no credit card required. Each free project includes 0.5 GB storage and 100 CU-hours per month; inactive compute scales to zero.

This remains a prototype configuration. Free quotas, sleep behavior, and service terms may change, so review provider limits before a long study.

## 1. Create the external database

1. Sign in at `https://console.neon.tech`.
2. Create a Free project in a region reasonably close to Render Singapore.
3. Keep the default `neondb` database or create a clearly named database.
4. Open **Connect**.
5. Choose the direct connection hostname, not the hostname containing `-pooler`.
6. Copy the connection string. It should resemble:

```text
postgresql://role:password@ep-example.region.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

Direct mode is deliberate: Flyway runs schema migrations at Ktor startup. Hikari performs application-side pooling.

## 2. Create the Render service

Open the Render Blueprint creation page for this repository. `render.yaml` creates only:

- one Docker web service on the Free plan;
- a generated session secret;
- secure cookie settings;
- a three-connection pool;
- health checks and CI-gated deploys.

When Render requests `DATABASE_URL`, paste the Neon connection string. `sync: false` prevents the value from being committed to the Blueprint.

Do not add `DEEPSEEK_API_KEY` yet unless desired. The database-guided fallback keeps the support chat demonstrable without it.

## 3. Verify first deployment

1. Confirm the Docker build completes.
2. Confirm Flyway reports four successful migrations.
3. Open `/health` and verify a `200` response.
4. Open `/login`, submit the blank prototype login, and reach the dashboard.
5. Search for `deadline` and verify seeded facilities appear.
6. Book one available slot, verify it in My Appointments, cancel it, and verify the slot returns.
7. Open Support Guide and confirm it reports `database-guided fallback` until the DeepSeek key is added.
8. Check Neon’s Tables view for seeded categories, facilities, slots, appointments, and conversations.

The deployed service passed steps 1–7 on 27 July 2026. The production check created and then cancelled a demonstration appointment, leaving the database record in `CANCELLED` state. The database-guided fallback responded without a DeepSeek key.

## 4. Add DeepSeek later

In Render:

**nmsi-support-navigator → Environment → Add Environment Variable**

```text
DEEPSEEK_API_KEY=<your secret>
DEEPSEEK_MODEL=deepseek-v4-flash
```

Save and redeploy. Never place either database credentials or the DeepSeek key in Git, Pebble templates, browser JavaScript, issues, screenshots, or Wiki pages.

## 5. Retire GitHub Pages

GitHub Pages was disabled on 27 July 2026 after the Render URL passed the verification checklist. The full-stack application cannot be hosted by GitHub Pages because Pages serves static files and cannot run Ktor or connect securely to PostgreSQL.

## Operational notes

- Render Free Web Services can sleep after inactivity and take time to wake.
- Neon compute also scales to zero while idle, so the first database request may be slower.
- The database project does not have Render’s 30-day expiry, but free-plan limits still apply.
- Export or upgrade the database before any real participant data collection.
- The current prototype login is not suitable for real personal data.
