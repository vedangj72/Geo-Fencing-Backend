# Geo-Fencing Backend (Ktor + Supabase PostgreSQL + Supabase Auth)

A high-performance Kotlin Ktor backend for location tracking and geofencing management using **Supabase PostgreSQL** as the primary data store and **Supabase Auth** for JWT user authentication.

---

## Architecture Overview

- **Database**: Supabase PostgreSQL (`postgresql://postgres:GeoFencing$1763@db.koigsgmvkvsmrgrvmfku.supabase.co:5432/postgres`)
- **Schema Management**: Managed via Supabase SQL Migrations / SQL Editor (Exposed is NOT used for migrations).
- **Authentication**: Supabase Auth (JWT Bearer Token validation in Ktor).
- **User Identity Security**: User IDs are extracted strictly from the validated JWT `sub` claim. Client-provided `userId` fields in request payloads are **never accepted**.

---

## Database Schema & Tables

### PostgreSQL Enums
- `group_role`: `ADMIN`, `MEMBER`
- `membership_status`: `ACTIVE`, `PENDING`, `REJECTED`, `LEFT`, `REMOVED`
- `invitation_type`: `DIRECT`, `LINK`, `CODE`
- `invitation_status`: `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `CANCELLED`
- `geofence_member_status`: `INSIDE`, `OUTSIDE`, `UNKNOWN`
- `geofence_event_type`: `ENTER`, `EXIT`, `DWELL`

### Tables Summary

1. `profiles`: User profiles linked directly to Supabase Auth (`id REFERENCES auth.users(id)`).
2. `groups`: Group records created by users (`created_by REFERENCES profiles(id)`).
3. `group_members`: Junction table mapping users to groups with roles & status (`UNIQUE(group_id, user_id)`).
4. `invitations`: Group invitations with expiration timestamps.
5. `geofences`: Active geofences per group. Enforces partial unique index:
   ```sql
   CREATE UNIQUE INDEX idx_unique_active_geofence_per_group ON geofences (group_id) WHERE is_active = true;
   ```
6. `member_geofence_status`: Live state tracker per user per group (`UNIQUE(group_id, user_id)`).
7. `geofence_events`: Audit log for all ENTER/EXIT/DWELL location events.

---

## Setup & Running Instructions

### 1. Environment Configuration (`.env`)
Create a `.env` file in the root directory (already added to `.gitignore`):

```env
SUPABASE_DB_URL=postgresql://postgres:GeoFencing$1763@db.koigsgmvkvsmrgrvmfku.supabase.co:5432/postgres
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=GeoFencing$1763

SUPABASE_URL=https://koigsgmvkvsmrgrvmfku.supabase.co
SUPABASE_ANON_KEY=your_anon_key_here
SUPABASE_JWT_SECRET=your_jwt_secret_here

PORT=8080
HOST=0.0.0.0
```

### 2. SQL Migrations Execution
Execute the SQL files in the Supabase SQL Editor in the following order:

1. `supabase/migrations/01_schema.sql` - Table definitions, enums, triggers, and indices.
2. `supabase/migrations/02_rls_policies.sql` - Row Level Security policies.
3. `supabase/seed.sql` - Development seed data (pre-populates `auth.users` identities for Vedang, Rahul, Amit, John, "Goa Trip" group, and geofence status).

### 3. Build & Run Application
```bash
# Build project
./gradlew build

# Run Ktor server
./gradlew run
```
The server will start on `http://0.0.0.0:8080`.

---

## Hosting on Free Cloud Platforms

### Option 1: Deploy on Render (Recommended)

1. **Push to GitHub**:
   Push this repository to your GitHub account (`git init`, `git add .`, `git commit`, `git push`).

2. **Create New Web Service on Render**:
   - Go to [dashboard.render.com](https://dashboard.render.com/) and click **New + -> Web Service**.
   - Connect your GitHub repository.

3. **Configure Settings**:
   - **Environment**: `Docker` (Render detects `Dockerfile` automatically).
   - **Plan**: `Free`.

4. **Add Environment Variables**:
   Under **Environment Variables**, add:
   - `SUPABASE_DB_URL`: `jdbc:postgresql://db.koigsgmvkvsmrgrvmfku.supabase.co:5432/postgres`
   - `SUPABASE_DB_USER`: `postgres`
   - `SUPABASE_DB_PASSWORD`: `GeoFencing$1763`
   - `SUPABASE_URL`: `https://koigsgmvkvsmrgrvmfku.supabase.co`
   - `SUPABASE_ANON_KEY`: `<your_key>`
   - `SUPABASE_JWT_SECRET`: `<your_secret>`

5. **Deploy**:
   Click **Create Web Service**. Render will automatically build the Docker container and host it on HTTPS:
   `https://geofencing-backend.onrender.com`

---

### Option 2: Deploy on Railway / Koyeb

1. Import your GitHub repository on Railway or Koyeb.
2. Select Docker build.
3. Paste the same Environment Variables as above.
4. Your API will be live in 1-2 minutes!


### Authentication Header
All protected endpoints require standard HTTP Authorization header:
```
Authorization: Bearer <SUPABASE_JWT>
```

### Endpoints

| Method | Path | Auth Required | Description |
|---|---|---|---|
| `GET` | `/health` | No | Public health check endpoint |
| `GET` | `/api/v1/profiles/me` | Yes | Get profile of authenticated user |
| `PUT` | `/api/v1/profiles/me` | Yes | Update profile details (name, phone, FCM token) |
| `GET` | `/api/v1/groups` | Yes | List groups for authenticated user |
| `POST` | `/api/v1/groups` | Yes | Create a new group |
| `GET` | `/api/v1/groups/{groupId}` | Yes | Get group detail, members, & active geofence |
| `POST` | `/api/v1/groups/{groupId}/geofence` | Yes | Set/update active geofence for group |
| `GET` | `/api/v1/groups/{groupId}/geofence` | Yes | Fetch active geofence for group |
| `POST` | `/api/v1/groups/{groupId}/geofence/events` | Yes | **Record geofence event (ENTER/EXIT/DWELL)** |
| `GET` | `/api/v1/groups/{groupId}/members/status` | Yes | Fetch geofence status of all group members |

---

## Security Verification (User ID Extraction)

### Example Request for Recording Event:
```http
POST /api/v1/groups/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/geofence/events
Authorization: Bearer <SUPABASE_JWT>
Content-Type: application/json

{
    "eventType": "EXIT",
    "latitude": 18.5400,
    "longitude": 73.8800,
    "occurredAt": "2026-08-15T12:30:00Z"
}
```

The user ID is **extracted strictly from the JWT `sub` claim** on the server side. Request payloads with `userId` are ignored / prohibited by design.
