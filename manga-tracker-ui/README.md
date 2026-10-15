# MangaTracker interface

React 19 interface for the MangaTracker Spring Boot API. See the repository README for full setup and Vercel deployment.

- `npm ci` installs the locked dependencies.
- `npm start` serves the interface at http://localhost:3000 and proxies API requests to http://localhost:8080.
- `npm run build` produces `build/`, which Maven packages into the backend.
- `CI=true npm test -- --watchAll=false --runInBand` runs frontend regression checks.

API requests use the current origin, server-verified login sessions, and CSRF headers. Passwords are sent in request bodies and never stored in local storage.
