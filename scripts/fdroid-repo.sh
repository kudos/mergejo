#!/usr/bin/env bash
# Rebuilds Mergejo's self-hosted F-Droid repository from the signed APKs on the latest cremin.dev
# releases, signs its index, and syncs it to an S3-compatible bucket. CI runs this after
# scripts/release.sh; by hand:
#
#   nix develop .#release -c bash scripts/fdroid-repo.sh
#
# The repository is derived entirely from the releases, so nothing is kept between runs: each APK's
# "added" date comes from its release's publish date.
#
# Environment:
#   FDROID_REPO_URL        public URL of the repository, ending in /repo
#   FDROID_KEYSTORE        PKCS12 keystore holding the repository (index) signing key
#   FDROID_KEY_ALIAS       its alias (default: mergejo-repo)
#   FDROID_KEYSTORE_PASS   keystore password
#   FDROID_KEY_PASS        key password (default: FDROID_KEYSTORE_PASS)
#   FDROID_S3_ENDPOINT, FDROID_S3_BUCKET, FDROID_S3_ACCESS_KEY, FDROID_S3_SECRET_KEY
#                          where to upload; the repository goes to <bucket>/repo. Without these
#                          the repository is only written to FDROID_OUT (default: build/fdroid).
#   FDROID_KEEP            how many releases to include (default: 3)
#   FORGEJO_API            repository API base (default: cremin.dev/jonathan/mergejo)
set -euo pipefail

APP_ID=dev.cremin.mergejo
API=${FORGEJO_API:-https://cremin.dev/api/v1/repos/jonathan/mergejo}
KEEP=${FDROID_KEEP:-3}

die() { echo "fdroid-repo: $*" >&2; exit 1; }
for var in FDROID_REPO_URL FDROID_KEYSTORE FDROID_KEYSTORE_PASS; do
    [ -n "${!var:-}" ] || die "$var is not set"
done
[[ $FDROID_REPO_URL == */repo ]] || die "FDROID_REPO_URL must end in /repo"
[ -f "$FDROID_KEYSTORE" ] || die "keystore $FDROID_KEYSTORE not found"
export FDROID_KEY_PASS=${FDROID_KEY_PASS:-$FDROID_KEYSTORE_PASS}

root=$(git rev-parse --show-toplevel)
listing=$root/fastlane/metadata/android/en-US
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
mkdir -p repo metadata

# Passwords stay in the environment; fdroidserver resolves {env: ...} itself.
cat > config.yml <<EOF
repo_url: $FDROID_REPO_URL
repo_name: Mergejo
repo_description: >-
  Releases of Mergejo, an app for triaging Renovate's dependency-update pull
  requests on Forgejo. Built and signed by the developer; F-Droid's main
  repository verifies the same APKs as reproducible builds.
archive_older: 0
make_current_version_link: false
keystore: $FDROID_KEYSTORE
repo_keyalias: ${FDROID_KEY_ALIAS:-mergejo-repo}
keystorepass: {env: FDROID_KEYSTORE_PASS}
keypass: {env: FDROID_KEY_PASS}
EOF
chmod 600 config.yml
# fdroidserver copies repo_icon (icon.png, relative to here) into repo/icons/.
cp "$listing/images/icon.png" icon.png

cat > "metadata/$APP_ID.yml" <<EOF
Categories:
  - Development
License: MIT
AuthorName: Jonathan Cremin
SourceCode: https://cremin.dev/jonathan/mergejo
IssueTracker: https://github.com/kudos/mergejo/issues
Changelog: https://cremin.dev/jonathan/mergejo/releases
AutoName: Mergejo
EOF
# Name, summary, description, icon, screenshots and changelogs come from the store listing.
mkdir -p "metadata/$APP_ID"
cp -r "$listing" "metadata/$APP_ID/en-US"

# The signed APK attached to each of the latest releases, dated by its release.
curl -sSf "$API/releases?draft=false&pre-release=false&limit=$KEEP" > releases.json
jq -r '.[] | .published_at as $date | .assets[]
       | select(.name | test("^mergejo-.*\\.apk$"))
       | [.name, .browser_download_url, $date] | @tsv' releases.json > apks.tsv
[ -s apks.tsv ] || die "no release APKs found at $API"
while IFS=$'\t' read -r name url date; do
    curl -sSfL -o "repo/$name" "$url"
    touch -d "$date" "repo/$name"
    echo "fdroid-repo: added $name ($date)"
done < apks.tsv

# Version info from each APK. The newest is the current version, and a Builds entry per version
# (never built here) is how fdroidserver attaches changelogs/<versionCode>.txt as "What's new".
aapt2s=("$ANDROID_HOME"/build-tools/*/aapt2)
aapt2=${aapt2s[-1]}
builds=""
first=1
while IFS=$'\t' read -r name _ _; do
    badging=$("$aapt2" dump badging "repo/$name")
    code=$(sed -n "s/^package: .* versionCode='\([0-9]*\)'.*/\1/p" <<< "$badging")
    version=$(sed -n "s/^package: .* versionName='\([^']*\)'.*/\1/p" <<< "$badging")
    # Releases are listed newest first; Builds must be oldest first.
    builds="  - versionName: $version
    versionCode: $code
    commit: v$version
$builds"
    if [ "$first" = 1 ]; then current="$version" current_code="$code"; first=0; fi
done < apks.tsv
printf 'Builds:\n%sCurrentVersion: %s\nCurrentVersionCode: %s\n' \
    "$builds" "$current" "$current_code" >> "metadata/$APP_ID.yml"

fdroid update --use-date-from-apk
[ -f repo/entry.jar ] && [ -f repo/index-v1.jar ] || die "fdroid didn't produce a signed index"

# What clients pin: SHA-256 of the repository signing certificate.
fingerprint=$(keytool -exportcert -keystore "$FDROID_KEYSTORE" -storepass:env FDROID_KEYSTORE_PASS \
    -alias "${FDROID_KEY_ALIAS:-mergejo-repo}" | sha256sum | cut -d' ' -f1)
if [ -z "${FDROID_S3_BUCKET:-}" ]; then
    out=${FDROID_OUT:-$root/build/fdroid}
    rm -rf "$out" && mkdir -p "$out" && cp -r repo "$out/"
    echo "fdroid-repo: no FDROID_S3_BUCKET, repository written to $out/repo (fingerprint $fingerprint)"
    exit 0
fi

for var in FDROID_S3_ENDPOINT FDROID_S3_ACCESS_KEY FDROID_S3_SECRET_KEY; do
    [ -n "${!var:-}" ] || die "$var is not set"
done
export RCLONE_S3_PROVIDER=Other RCLONE_S3_ENDPOINT=$FDROID_S3_ENDPOINT \
    RCLONE_S3_ACCESS_KEY_ID=$FDROID_S3_ACCESS_KEY RCLONE_S3_SECRET_ACCESS_KEY=$FDROID_S3_SECRET_KEY \
    RCLONE_S3_FORCE_PATH_STYLE=true RCLONE_S3_NO_CHECK_BUCKET=true
rclone sync --checksum repo ":s3:$FDROID_S3_BUCKET/repo"
echo "fdroid-repo: published $FDROID_REPO_URL?fingerprint=$fingerprint"
