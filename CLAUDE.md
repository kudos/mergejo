# GitOps Android — agent notes

Native Android app to triage the **Renovate** dependency-update PRs on the self-hosted
**Forgejo at `cremin.dev`**. One aggregated list across all `jonathan/*` repos, rich PR
detail (version bump, CI status, release notes), and per-PR actions: merge / approve /
rebase / close. Compose + Material 3, Hilt, Retrofit + kotlinx.serialization, DataStore.
Single `:app` module, single Activity + NavHost, MVVM, repository-as-seam. `applicationId`
`dev.cremin.gitops`.

## Build / run — nix-native (this box: NixOS, ~3.7 GB RAM)

Everything goes through the flake dev shell; there is no global JDK/SDK/Gradle.

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
- **Memory**: this box has ~3.7 GB RAM. `gradle.properties` caps the heap at 1536m with
  no daemon / no parallel workers / Kotlin in-process. Android compilation still needs
  swap — there's a 6 GB swapfile at `/var/gitops-build-swap` (ephemeral; recreate with
  `sudo fallocate -l 6G /var/gitops-build-swap && sudo chmod 600 … && sudo mkswap … && sudo swapon …`,
  or make it declarative in the NixOS config). Without swap `compileDebugKotlin` gets OOM-killed.
- Kotlin block comments **nest**: never put a bare `/*` (e.g. `renovate/*`) inside a KDoc.

## Auth — OAuth2 (Authorization Code + PKCE) via AppAuth

1. Register an OAuth2 app at `https://cremin.dev/user/settings/applications`, redirect URI
   `dev.cremin.gitops:/oauth2redirect`.
2. Put the client id in `local.properties` as `OAUTH_CLIENT_ID=…` (read into `BuildConfig`).
   `FORGEJO_BASE_URL` defaults to `https://cremin.dev`.
- AppAuth discovers endpoints from `/.well-known/openid-configuration`; Forgejo may SSO the
  login through Pocket ID (`id.cremin.app`). Tokens are Forgejo tokens used as
  `Authorization: Bearer` against `/api/v1`. `AuthState` is persisted in EncryptedSharedPreferences.

## Layout

```
data/auth/          AppAuth wrapper (AuthManager), encrypted store, OkHttp AuthInterceptor
data/remote/{api,dto}/   ForgejoApi (Retrofit) + Gitea/Forgejo DTOs (JsonNamingStrategy.SnakeCase)
data/repository/    RenovateRepository — the seam: aggregate → filter head.ref renovate/ → enrich (CI) → actions
data/preferences/   SettingsRepository (DataStore)
domain/             RenovatePrParser (title/body → update type, versions, security), RebaseCheckbox
ui/screen/{login,list,detail,settings}/   Screen + ViewModel each; ui/navigation, ui/component
di/                 NetworkModule, AuthModule
```

## Conventions

- Renovate PRs are identified by the **`renovate/` head-branch prefix** (no labels are configured
  on cremin.dev). Update type is parsed heuristically from the PR title + body change table.
- Merge/close hit the **issues** endpoint (PRs are issues); rebase ticks Renovate's
  `<!-- rebase-check -->` checkbox in the PR body (effect applies on Renovate's next 4-hourly run).
- Keep the `:app`-only, repository-as-seam, KSP-only, strings-in-`res/values` conventions.
- Out of scope for v1 (clean seams exist): notifications, batch actions, Dependency Dashboard control.

## CI

`.forgejo/workflows/build.yml` runs `nix develop -c ./gradlew testDebugUnitTest assembleDebug`
on a self-hosted runner (needs Nix with flakes) — no Docker.
