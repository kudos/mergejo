#!/usr/bin/env bash
# Builds the signed release APK for the v<versionName> tag at HEAD and publishes it as a Forgejo
# release, with the fastlane changelog as release notes. CI runs this on tag pushes; it also works
# by hand from a clean checkout of the tag:
#
#   nix develop -c bash scripts/release.sh
#
# Signing comes from RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS /
# RELEASE_KEY_PASSWORD in local.properties or the environment. Publishing needs FORGEJO_TOKEN
# (repository write access); without it the script stops after building and checking the APK.
#
# F-Droid builds the same tag itself and only publishes this APK if the two match byte for byte
# (reproducible builds), so the APK must come from a clean, normal checkout of the tag.
set -euo pipefail

# SHA-256 of the release signing certificate (also AllowedAPKSigningKeys in fdroiddata).
EXPECTED_CERT_SHA256=a0961d0c686c70f135ef36722e6c8431daffb1062baaa03a29fd14d543a88560
API=${FORGEJO_API:-https://cremin.dev/api/v1/repos/jonathan/mergejo}

die() { echo "release: $*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"

version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' app/build.gradle.kts)
code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' app/build.gradle.kts)
[ -n "$version" ] && [ -n "$code" ] || die "couldn't read versionName/versionCode from app/build.gradle.kts"
tag="v$version"
notes="fastlane/metadata/android/en-US/changelogs/$code.txt"
apk="mergejo-$version.apk"

# AGP records the commit in the APK, and only finds it from a normal checkout.
[ -d .git ] || die "run from a normal git checkout, not a worktree"
[ "$(git rev-parse HEAD)" = "$(git rev-parse -q --verify "$tag^{commit}" || true)" ] \
    || die "HEAD isn't tagged $tag (versionName is $version)"
[ -z "$(git status --porcelain)" ] || die "the checkout has uncommitted or untracked files"
[ -f "$notes" ] || die "missing changelog $notes"

echo "release: building $tag (versionCode $code)"
bash ./gradlew --no-daemon clean assembleRelease
built=app/build/outputs/apk/release/app-release.apk
[ -f "$built" ] || die "no signed APK at $built; is release signing configured?"

apksigners=("$ANDROID_HOME"/build-tools/*/apksigner)
apksigner=${apksigners[-1]}
cert=$("$apksigner" verify --print-certs "$built" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[ "$cert" = "$EXPECTED_CERT_SHA256" ] \
    || die "APK is signed with certificate $cert, expected $EXPECTED_CERT_SHA256"
cp "$built" "$apk"
echo "release: $apk signed with the release key"

if [ -z "${FORGEJO_TOKEN:-}" ]; then
    echo "release: FORGEJO_TOKEN not set, not publishing"
    exit 0
fi

auth="Authorization: token $FORGEJO_TOKEN"
status=$(curl -sS -o release.json -w '%{http_code}' -H "$auth" "$API/releases/tags/$tag")
if [ "$status" = 404 ]; then
    jq -n --arg tag "$tag" --arg name "Mergejo $version" --rawfile body "$notes" \
        '{tag_name: $tag, name: $name, body: $body}' > request.json
    curl -sS --fail-with-body -o release.json -X POST -H "$auth" -H "Content-Type: application/json" \
        --data @request.json "$API/releases"
    echo "release: created release $tag"
elif [ "$status" != 200 ]; then
    cat release.json >&2
    die "looking up release $tag failed with HTTP $status"
fi
id=$(jq -r .id release.json)

if jq -e --arg n "$apk" 'any(.assets[]; .name == $n)' release.json > /dev/null; then
    echo "release: $apk is already attached to $tag, leaving it"
else
    curl -sS --fail-with-body -o /dev/null -X POST -H "$auth" \
        -F "attachment=@$apk" "$API/releases/$id/assets?name=$apk"
    echo "release: attached $apk to $tag"
fi
rm -f release.json request.json
