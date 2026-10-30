# Mergejo — agent notes

Mergejo (repo `mergejo`) is a native Android app to triage the **Renovate** dependency-update PRs on the self-hosted
**Forgejo at `cremin.dev`**. One aggregated list across all `jonathan/*` repos, rich PR
detail (version bump, CI status, release notes), and per-PR actions: merge / approve /
rebase / close. Compose + Material 3, Hilt, Retrofit + kotlinx.serialization, DataStore.
Single `:app` module, single Activity + NavHost, MVVM, repository-as-seam. `applicationId`
`dev.cremin.mergejo`.

## Build / run — nix-native

Everything goes through the flake dev shell; the build doesn't rely on a global JDK/SDK/Gradle.

```sh
nix develop -c ./gradlew assembleDebug        # build debug APK
nix develop -c ./gradlew installDebug         # install on attached device/emulator
nix develop -c ./gradlew testDebugUnitTest    # unit tests
```

- `flake.nix` provides the Android SDK (platform 35, build-tools 35.0.0), `jdk21`
  (Gradle daemon) and `jdk17` (Kotlin/AGP toolchain, via `JDK_17`/`JDK_21` +
  `org.gradle.java.installations.fromEnv`). No foojay downloads.
- **aapt2**: NixOS can't run AGP's Maven aapt2. The flake sets
  `GRADLE_OPTS=-Dorg.gradle.project.android.aapt2FromMavenOverride=$SDK/build-tools/35.0.0/aapt2`
  so AGP uses the patched SDK aapt2. Don't remove this or resource processing fails.
- **Memory**: `gradle.properties` keeps the build in one 1536m JVM (no daemon, no parallel
  workers, Kotlin in-process). Per-machine overrides belong in `~/.gradle/gradle.properties`,
  not here.
- Kotlin block comments **nest**: never put a bare `/*` (e.g. `renovate/*`) inside a KDoc.

## Auth — personal access token

- Login is server URL + Forgejo personal access token (scopes `read:user`, `write:repository`,
  `write:issue`); no OAuth, no build-time client id. `FORGEJO_BASE_URL` in `local.properties`
  only sets the prefilled server URL (default `https://codeberg.org`).
- `AuthManager` signs in in two phases: `stage` the entered credentials, validate with
  `GET /api/v1/user`, then `confirm` (persist) or `rollback`. Both are stored in
  EncryptedSharedPreferences (`CredentialStore`).
- `AuthInterceptor` rewrites each request's scheme/host/port to the configured server and adds
  `Authorization: token <token>`, so any Forgejo instance works without rebuilding (assumes the
  instance is served at the host root, no path prefix).

## Layout

```
data/auth/          AuthManager (credentials + sign-in state), CredentialStore, OkHttp AuthInterceptor
data/remote/{api,dto}/   ForgejoApi (Retrofit) + Gitea/Forgejo DTOs (JsonNamingStrategy.SnakeCase)
data/repository/    RenovateRepository — the seam: aggregate → filter head.ref renovate/ → enrich (CI) → actions
data/preferences/   SettingsRepository (DataStore)
domain/             RenovateBodyParser (body → RenovateBody: table rows, notices, advisories, per-release notes, config),
                    RenovatePrParser (PR-level summary on top of it), RenovateMarkdown (cleanup for the renderer), RebaseCheckbox,
                    NewPrDetector (which PRs a background poll should announce)
notifications/      PrPollScheduler (unique periodic WorkManager job), PrPollWorker (@HiltWorker), PrNotifier
ui/screen/{login,list,detail,settings}/   Screen + ViewModel each; ui/navigation, ui/component
di/                 NetworkModule, AuthModule
```

## Conventions

- Renovate PRs are identified by the **`renovate/` head-branch prefix** (no labels are configured
  on cremin.dev). The body is parsed into structured data (`RenovateBody`) by locating Renovate's template
  anchors (table separator, `### Release Notes`, `### Configuration`), not by splitting on `---` — changelogs
  contain their own rules and nested `<details>`. The detail screen renders from `RenovateBody`; raw markdown
  is only shown when the body isn't Renovate's template. Parser fixtures (real bodies) live in
  `app/src/test/resources/renovate/`.
- Merge/close hit the **issues** endpoint (PRs are issues); rebase ticks Renovate's
  `<!-- rebase-check -->` checkbox in the PR body (effect applies on Renovate's next 4-hourly run).
- Keep the `:app`-only, repository-as-seam, KSP-only, strings-in-`res/values` conventions.
- New-PR notifications are polling via WorkManager (no push), off by default; the interval lives in
  `SettingsRepository`. WorkManager's default initializer is removed in the manifest so
  `MergejoApplication` can supply `HiltWorkerFactory`. Seen PR ids are a union (not the last poll's
  set) so a repo that fails to list once doesn't get its PRs re-announced; enabling starts from a
  silent baseline. A notification tap passes the PR id to `MainActivity` (`EXTRA_PR_ID`) → `AppNavHost`.
- Out of scope for v1 (clean seams exist): batch actions, Dependency Dashboard control.

## CI

`.forgejo/workflows/build.yml` runs `nix develop -c bash ./gradlew testDebugUnitTest assembleDebug assembleBenchmark`
on the self-hosted NixOS runner (label `nix-host`, needs Nix with flakes) — no Docker — then publishes
`mergejo-debug.apk` and `mergejo.apk` (benchmark) to the generic package registry under
`generic/mergejo/<sha>/`.
