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

- `flake.nix` provides the Android SDK (platform 35, build-tools 35.0.0) and `jdk21`. There are no
  Java toolchains: the build compiles with the JDK running Gradle and targets Java 17 bytecode
  (`compileOptions` + Kotlin `jvmTarget`). Don't reintroduce `jvmToolchain(17)`: F-Droid's build
  server only has JDK 21, and JDK auto-download is off.
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
data/repository/    RenovateRepository — the seam: aggregate (every page, via Link rel="next") → filter head.ref renovate/ → enrich (CI) → actions
data/preferences/   SettingsRepository (DataStore)
domain/             RenovateBodyParser (body → RenovateBody: table rows, notices, advisories, per-release notes, config),
                    RenovatePrParser (PR-level summary on top of it), RenovateMarkdown (cleanup for the renderer), RebaseCheckbox,
                    NewPrDetector (which PRs a background poll should announce),
                    CommentParser (PR comment → prose + top-level <details> blocks, for bot output such as closure diffs)
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
- PR comments are loaded by the detail screen (not by `refresh`, which would cost a request per PR) and reloaded
  when the head commit changes, since CI bots rewrite their comment per push. Each top-level `<details>` becomes an
  expandable card; a body that is a single fenced code block is shown verbatim and scrolls sideways instead of
  wrapping. Fixtures live in `app/src/test/resources/comments/`.
- Out of scope for v1 (clean seams exist): batch actions, Dependency Dashboard control.

## Hosting

Canonical repo: `cremin.dev/jonathan/mergejo` (F-Droid builds from it). `github.com/kudos/mergejo` is a
push mirror from Forgejo, used for public issues because cremin.dev has registration disabled.
Don't push to the mirror directly.

## Releases

Reproducible builds: F-Droid builds each `v<versionName>` tag and only publishes the developer-signed APK
attached to the cremin.dev release as `mergejo-<versionName>.apk` (fdroiddata `Binaries` +
`AllowedAPKSigningKeys`). The Nix build is byte-identical to F-Droid's Debian/JDK 21 build except
`META-INF/version-control-info.textproto`, which AGP only fills in from a normal git checkout — so build
releases from a clean checkout of the tag, never a worktree. Signing is Gradle's `release` signingConfig from
`local.properties` or the environment (`RELEASE_*`); don't switch to `apksigner` from build-tools 35+ (F-Droid
can't verify it). Pushing a `v*` tag runs CI's `release` job → `scripts/release.sh` (checks tag/versionName/changelog,
builds, pins the signing cert fingerprint, creates the Forgejo release and attaches the APK; idempotent). Keystore and
passwords come from repo secrets; the release is created with the workflow's own token. `dependenciesInfo` is off in
`app/build.gradle.kts`: F-Droid's CI scanner fails on the encrypted "Dependency metadata" signing block AGP otherwise
adds to signed APKs. Release steps are in the README.

Self-hosted F-Droid repo: the release job's last step runs `scripts/fdroid-repo.sh` in `nix develop .#release`
(fdroidserver + rclone). It downloads the APKs from the latest 3 releases, writes throwaway `config.yml` and
`metadata/` (store listing copied from `fastlane/`; a `Builds` entry per version only so fdroidserver attaches each
changelog as "What's new"), runs `fdroid update --use-date-from-apk` (APK mtimes = release dates, so it's stateless),
and `rclone sync`s `repo/` to the S3 bucket (RustFS). fdroidserver reads `repo_icon` relative to the working dir,
not `repo/icons/`. Config is `FDROID_*` repo variables/secrets; the step is skipped while `FDROID_REPO_URL` is unset.

## CI

`.forgejo/workflows/build.yml` runs `nix develop -c bash ./gradlew testDebugUnitTest assembleDebug assembleBenchmark`
on the self-hosted NixOS runner (label `nix-host`, needs Nix with flakes) — no Docker — then publishes
`mergejo-debug.apk` and `mergejo.apk` (benchmark) to the generic package registry under
`generic/mergejo/<sha>/` — branch pushes only, since a tag's commit is already published there and the registry
answers re-uploads with 409. On `v*` tags a second job, `release`, publishes the signed release (see Releases).
