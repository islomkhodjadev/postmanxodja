# PostBaby Desktop

JavaFX 21 desktop client for the PostBaby API. Phases 1 + 2 ship:

**Phase 1** — sign-in, editor, request execution
- Google OAuth sign-in via the system browser + a loopback callback (`http://127.0.0.1:53682/`)
- Local-first cache: collections, environments, tokens, tabs, and active selections live in JSON files under the OS data dir
- Collection tree (Postman v2.1) with request editor (method, URL, params, headers, raw body)
- Environment dropdown with `{{var}}` substitution applied at send-time
- Direct request execution from the desktop (no backend round-trip), with response viewer (status, time, body, headers)
- Save edits locally; push to backend when online (`File → Sync with backend`)

**Phase 2** — tabs, teams, invites, API keys
- Multi-tab editor: opening a request from the tree opens a new tab; existing tabs are focused. Each tab keeps its own URL, method, params, headers, body, and last response.
- Tabs sync via `/tabs` — debounced 1.5 s after edits. Active tab is remembered.
- Teams dialog: list members, send invites by email, remove members (owner-only), leave or delete the team.
- Invites dialog: pending invite inbox with accept/decline. Toolbar shows a count badge.
- API keys dialog: list keys with prefix/permissions/expiry, create with permission level (`read` / `write` / `read_write`) and optional expiry-in-days, copy-once-on-create, delete.

## Requirements

- JDK 21 (any vendor — Temurin, Corretto, or Homebrew openjdk@21)
- Internet for OAuth sign-in and team/collection sync. Request execution and editing work fully offline.

## Run from source

```bash
cd desktop
./gradlew run
```

By default the app talks to `https://postbaby.uz/api`. Override via system property or env var:

```bash
./gradlew run -Dpostbaby.api.base=http://localhost:8080/api
# or
POSTBABY_API_BASE=http://localhost:8080/api ./gradlew run
# or, shorthand for the local backend:
./gradlew run -Dpostbaby.profile=local
```

The endpoint can also be changed at runtime from the login screen.

## Data location

| OS      | Path                                                       |
|---------|------------------------------------------------------------|
| macOS   | `~/Library/Application Support/PostBaby/`                  |
| Windows | `%APPDATA%\PostBaby\`                                      |
| Linux   | `~/.local/share/postbaby/`                                 |

Files: `tokens.json`, `teams.json`, `team_<id>_collections.json`, `team_<id>_environments.json`, `user_<id>_tabs.json`, `settings.json`.

## Build native installers

```bash
# Run on the target OS — jpackage cannot cross-compile installers.
./gradlew jpackage
```

Output:

- macOS: `build/jpackage/PostBaby-0.1.0.dmg`
- Windows: `build\jpackage\PostBaby-0.1.0.exe`
- Linux: `build/jpackage/postbaby_0.1.0_amd64.deb`

> Icons are referenced from `src/main/resources/icons/postbaby.icns` (mac) and `postbaby.ico` (win).
> Drop those files in before running `jpackage`, or remove the `icon` lines in `build.gradle.kts`.

## Backend changes shipped with Phase 1

`GET /api/auth/google` now accepts `?desktop_port=NNNN`. When set, the OAuth callback redirects to `http://127.0.0.1:NNNN/?access_token=...&refresh_token=...` instead of the web frontend. Web flow is unchanged when the parameter is absent.

## Phase 3 (not in this drop)

- AI settings (DBML analyze)
- cURL / Postman / uCode importers
- Multipart / form-data bodies
- Authorization helpers (Bearer / Basic / API key shortcuts)
