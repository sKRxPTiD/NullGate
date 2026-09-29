#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DIST_DIR="$BASE_DIR/dist"
commit="$(git -C "$BASE_DIR" rev-parse HEAD)"
branch="$(git -C "$BASE_DIR" branch --show-current)"
short_commit="${commit:0:12}"
OUTPUT_DIR="$DIST_DIR/release-candidate/0.3.0-$short_commit"
PAYLOAD_NAME="NullGate-PiXi-private-0.3.0"
BUNDLE_NAME="${PAYLOAD_NAME}-2026-09-29-$short_commit.zip"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AAPT2="${AAPT2_BIN:-$SDK/build-tools/36.0.0/aapt2}"
APKSIGNER="${APKSIGNER_BIN:-$SDK/build-tools/36.0.0/apksigner}"
die() { echo "NullGate 0.3.0 packaging: $*" >&2; exit 1; }

command -v zip >/dev/null || die "zip is unavailable"
command -v unzip >/dev/null || die "unzip is unavailable"
command -v gzip >/dev/null || die "gzip is unavailable"
command -v sha256sum >/dev/null || die "sha256sum is unavailable"
[[ -x "$AAPT2" && -x "$APKSIGNER" ]] || die "Android build tools are unavailable"
git -C "$BASE_DIR" diff --quiet && git -C "$BASE_DIR" diff --cached --quiet \
  || die "commit the reviewed tracked source before packaging"
mapfile -d '' -t untracked < <(git -C "$BASE_DIR" ls-files --others --exclude-standard -z)
for path in "${untracked[@]}"; do
  [[ "$path" == 'ha256sum -c NullGate-PiXi-private-2026-09-26.zip.sha256' ]] \
    || die "untracked source must be accounted for before packaging: $path"
done
if [[ -e "$OUTPUT_DIR" ]]; then
  [[ -d "$OUTPUT_DIR" && ! -L "$OUTPUT_DIR" \
     && -f "$OUTPUT_DIR/$BUNDLE_NAME" && ! -L "$OUTPUT_DIR/$BUNDLE_NAME" \
     && ! -L "$OUTPUT_DIR/$BUNDLE_NAME.sha256" \
     && ( ! -e "$OUTPUT_DIR/$BUNDLE_NAME.sha256" || -f "$OUTPUT_DIR/$BUNDLE_NAME.sha256" ) ]] \
    || die "staging directory is not the exact recoverable partial output; inspect it without overwriting"
  extra="$(find "$OUTPUT_DIR" -mindepth 1 -maxdepth 1 \
    ! -name "$BUNDLE_NAME" ! -name "$BUNDLE_NAME.sha256" -print -quit)"
  [[ -z "$extra" ]] || die "unexpected item exists in the staging directory: $extra"
fi
[[ -s "$DIST_DIR/NullGate-prototype-debug.apk" && -s "$DIST_DIR/NullGate-broker.jar" \
   && -s "$DIST_DIR/controller-cert-sha256.txt" && -s "$DIST_DIR/SHA256SUMS" ]] \
  || die "default production build artifacts are incomplete"
(cd "$DIST_DIR" && sha256sum --status -c SHA256SUMS) \
  || die "default build checksum verification failed"
"$BASE_DIR/release/check-release-metadata.sh" --with-apk

expected_signer="$(sed -n 's/^controller\.signer\.sha256=//p' \
  "$BASE_DIR/compat/pixi-private-pins.properties")"
actual_signer="$("$APKSIGNER" verify --print-certs "$DIST_DIR/NullGate-prototype-debug.apk" \
  | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[[ "$expected_signer" =~ ^[0-9a-f]{64}$ && "$actual_signer" == "$expected_signer" ]] \
  || die "production APK does not use the pinned paired signer"
badging="$("$AAPT2" dump badging "$DIST_DIR/NullGate-prototype-debug.apk" | sed -n '1p')"
[[ "$badging" == *"name='org.nullprotocol.nullgate'"* \
   && "$badging" == *"versionCode='4'"* && "$badging" == *"versionName='0.3.0'"* ]] \
  || die "production APK identity is not 0.3.0/code 4"

stage_root="$(mktemp -d)"
trap 'rm -rf -- "$stage_root"' EXIT
payload="$stage_root/$PAYLOAD_NAME"
mkdir -p "$payload/artifacts" "$payload/operator" "$payload/integration" "$payload/source"
install -m 0644 "$DIST_DIR/NullGate-prototype-debug.apk" "$payload/artifacts/NullGate-prototype-debug.apk"
install -m 0644 "$DIST_DIR/NullGate-broker.jar" "$payload/artifacts/NullGate-broker.jar"
install -m 0644 "$DIST_DIR/controller-cert-sha256.txt" "$payload/artifacts/controller-cert-sha256.txt"
install -m 0644 "$BASE_DIR/release/PIXI_0.3.0_PRIVATE_OPERATIONS.md" "$payload/README.md"
install -m 0644 "$BASE_DIR/root-client/USAGE.md" "$payload/operator/USAGE.md"
install -m 0644 "$BASE_DIR/root-client/VALIDATION.md" "$payload/operator/VALIDATION.md"
install -m 0755 "$BASE_DIR/device/nullgate-root-session.sh" "$payload/operator/nullgate-root-session.sh"
install -m 0644 "$BASE_DIR/integrations/patches/v3-0003-Add-NullGate-manual-root-bridge.patch" \
  "$payload/integration/colorblendr-nullgate-root-bridge.patch"

git -C "$BASE_DIR" archive --format=tar "$commit" | gzip -n -9 \
  > "$payload/source/NullGate-source-$short_commit.tar.gz"
# Include an artifact-only manifest usable directly by the session helper.
(cd "$payload/artifacts" && sha256sum NullGate-prototype-debug.apk NullGate-broker.jar \
  controller-cert-sha256.txt > SHA256SUMS)
{
  printf 'bundle=%s\n' "$PAYLOAD_NAME"
  printf 'created_utc=2026-09-29\nsource_branch=%s\nsource_commit=%s\n' "$branch" "$commit"
  printf 'source_worktree=clean_except_protected_untracked_record\n'
  printf 'controller_package=org.nullprotocol.nullgate\ncontroller_version=0.3.0\ncontroller_version_code=4\n'
  printf 'broker=NullGate-broker.jar\nsigner_sha256=%s\n' "$actual_signer"
  printf 'source_archive=source/NullGate-source-%s.tar.gz\nkeystore_included=no\npassword_file_included=no\n' "$short_commit"
} > "$payload/BUILD_INFO.txt"
(cd "$payload" && find . -type f ! -path ./SHA256SUMS -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS)
find "$payload" -type f -exec touch -d @946684800 -- {} +
bundle_tmp="$stage_root/$BUNDLE_NAME"
(cd "$stage_root" && find "$PAYLOAD_NAME" -type f -print | LC_ALL=C sort \
  | zip -X -q "$bundle_tmp" -@)

mkdir -p "$OUTPUT_DIR"
if [[ -e "$OUTPUT_DIR/$BUNDLE_NAME" ]]; then
  existing_manifest="$(unzip -p "$OUTPUT_DIR/$BUNDLE_NAME" "$PAYLOAD_NAME/SHA256SUMS")" \
    || die "existing partial ZIP has no internal checksum manifest"
  [[ "$existing_manifest" == "$(<"$payload/SHA256SUMS")" ]] \
    || die "existing partial ZIP contains a different payload; it was not overwritten"
  unzip -tq "$OUTPUT_DIR/$BUNDLE_NAME" >/dev/null \
    || die "existing partial ZIP failed its integrity check"
  while read -r expected relative_path; do
    [[ "$expected" =~ ^[0-9a-f]{64}$ && "$relative_path" == ./* ]] \
      || die "malformed internal checksum manifest"
    archived_path="$PAYLOAD_NAME/${relative_path#./}"
    actual="$(unzip -p "$OUTPUT_DIR/$BUNDLE_NAME" "$archived_path" | sha256sum | awk '{print $1}')"
    [[ "$actual" == "$expected" ]] || die "existing ZIP payload failed its checksum: $relative_path"
  done <<< "$existing_manifest"
else
  cp -- "$bundle_tmp" "$OUTPUT_DIR/$BUNDLE_NAME"
  [[ "$(sha256sum "$bundle_tmp" | awk '{print $1}')" == \
     "$(sha256sum "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')" ]] \
    || die "bundle bytes changed during the TerraDrive write"
fi
unzip -tq "$OUTPUT_DIR/$BUNDLE_NAME" >/dev/null || die "staged archive failed ZIP integrity check"
if [[ -e "$OUTPUT_DIR/$BUNDLE_NAME.sha256" ]]; then
  (cd "$OUTPUT_DIR" && sha256sum --status -c "$BUNDLE_NAME.sha256") \
    || die "existing staged sidecar does not match the bundle"
else
  (cd "$OUTPUT_DIR" && sha256sum "$BUNDLE_NAME" > "$BUNDLE_NAME.sha256")
fi
unzip -tq "$OUTPUT_DIR/$BUNDLE_NAME" >/dev/null || die "staged archive failed ZIP integrity check"
echo "Private 0.3.0 operations bundle staged; immutable releases/ was not touched."
echo "Bundle: $OUTPUT_DIR/$BUNDLE_NAME"
echo "Checksum: $OUTPUT_DIR/$BUNDLE_NAME.sha256"
