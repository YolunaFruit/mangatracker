# MangaTracker

A React + Spring Boot app for discovering manga and keeping a personal reading library. Browse trending and recently updated titles, search titles/authors, include or exclude tags, discover a random title, and track chapters, status, and personal scores.

The React source lives in `manga-tracker-ui/` as ordinary tracked files. It was previously an incomplete Git submodule; a fresh clone now includes the interface.

## Run locally

Use Java 21 and Node.js 22 LTS. Maven is included through the wrapper.

Start the backend from the repository root:

```sh
./mvnw spring-boot:run
```

In another terminal:

```sh
cd manga-tracker-ui
npm ci
npm start
```

Open `http://localhost:3000`. The React development server forwards API requests to port 8080. Register an account and then log in. Passwords must have at least eight characters and fit within 72 UTF-8 bytes. Existing local plaintext passwords are upgraded to bcrypt after a successful login.

Local data is stored in `data/`, which is ignored by Git. The original database is not bundled or deployed, and tests use a separate in-memory database. Keep a backup before using an older H2 database with the upgraded application.

## Production build

```sh
cd manga-tracker-ui
npm ci
npm run build
cd ..
./mvnw package
java -jar target/MangaTracker-0.0.1-SNAPSHOT.jar
```

The built React files are included in the Java application. Open `http://localhost:8080`.

## Deploy to Vercel

The `app` service in `vercel.json` explicitly selects Vercel's container runtime and routes every request to `Dockerfile.vercel` at the repository root. It builds the React app, runs the backend tests, and packages a Java 21 application that serves both the interface and API. Keep the project root at the repository root; do not select `manga-tracker-ui` as the root directory or apply the Create React App framework preset.

Before deploying, connect a persistent PostgreSQL database (for example, through Vercel's Neon integration) and set these environment variables:

| Variable | Purpose |
| --- | --- |
| `PORT` | Set this to `8080` in the Vercel project so routing matches the Java server. |
| `DATABASE_URL` | PostgreSQL connection string, such as the one supplied by Neon. `postgres://`, `postgresql://`, and JDBC PostgreSQL URLs are supported. |
| `DATABASE_USERNAME` | Required only when the URL does not contain credentials. |
| `DATABASE_PASSWORD` | Required only when the URL does not contain credentials. |
| `MAL_CLIENT_ID` | Optional MyAnimeList API client ID. Without it, MangaDex remains available. |

The container sets `SPRING_PROFILES_ACTIVE=production` and `PORT=8080`. Production requires PostgreSQL and stores login sessions there as well, so accounts, libraries, and sessions survive instance restarts. TLS cookies are enabled in production. Never use the local H2 file or an ephemeral `/tmp` database on Vercel.

Deploy with the Vercel dashboard or CLI. `/health` returns `{"status":"ok"}` when the app is serving requests. Verify registration, login, search, library edits, logout, and that saved progress remains after redeployment.

Vercel container documentation: https://vercel.com/docs/functions/runtimes/container

## Checks

```sh
./mvnw test
cd manga-tracker-ui
CI=true npm test -- --watchAll=false --runInBand
npm run build
```

The regression checks cover real cookie sessions, CSRF, account ownership, password hashing, duplicate entries, manual progress, status validation, score clearing, random discovery without a search query, legacy IDs, chapter numbering gaps, PostgreSQL URL configuration, and frontend session recovery.

## Data-source behavior

MangaDex does not always have all translated chapters. The app displays the highest available numbered English chapter, not the number of available chapter records. Manual reading progress may exceed that number and is never reset when metadata is missing. Latest-chapter results expire after five minutes; failed requests are not cached as zero.

Search requests expand cover and author relationships to avoid a separate API call per result. Library metadata uses a maximum of three concurrent lightweight requests. MyAnimeList remains optional and requires your own API client ID.
