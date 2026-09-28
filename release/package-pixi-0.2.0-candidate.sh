#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DIST_DIR="$BASE_DIR/dist"
OUTPUT_DIR="$DIST_DIR/release-candidate"
BUNDLE_NAME="NullGate-PiXi-private-0.2.0-rc1-2026-09-28.zip"
PAYLOAD_NAME="NullGate-PiXi-private-0.2.0-rc1"
REPRODUCIBLE_EPOCH=946684800
PROTECTED_UNTRACKED="ha256sum -c NullGate-PiXi-private-2026-09-26.zip.sha256"
EXPECTED_MANIFEST='7c5c2ed63eace24c4cc318c8833b5f1da6265abbe1ae4e4af5524dc1cce99b7d  NullGate-prototype-debug.apk
b9c22a64dc47b80a266390397cb8a081058d3e32fea857c648b9f5a2269b7e7e  NullGate-test-client-debug.apk
ebe16630f37141bee790fcdfadd1ab2b1878e4f1f5fd74d3bdd7f8e2f27536e0  NullGate-theme-client-debug.apk
d8bfb818754dea0d4d986e359e2b958056e233bb632a0ca7d00ee3f3b959fb52  NullGate-broker.jar
40dc996cea23649265de3b0ff1629dc17db385ab423dcb3266aaa849c5e6f547  controller-cert-sha256.txt'

die() { echo "NullGate candidate packaging: $*" >&2; exit 1; }

command -v git >/dev/null || die "git is unavailable"
command -v zip >/dev/null || die "zip is unavailable"
command -v gzip >/dev/null || die "gzip is unavailable"
command -v sha256sum >/dev/null || die "sha256sum is unavailable"
[[ ! -e "$OUTPUT_DIR" ]] || die "candidate output already exists; review it before another staging run"

git -C "$BASE_DIR" diff --quiet || die "tracked working-tree changes must be committed first"
git -C "$BASE_DIR" diff --cached --quiet || die "staged changes must be committed first"
mapfile -d '' -t untracked < <(git -C "$BASE_DIR" ls-files --others --exclude-standard -z)
for path in "${untracked[@]}"; do
  [[ "$path" == "$PROTECTED_UNTRACKED" ]] \
    || die "unexpected untracked path blocks candidate staging: $path"
done

"$BASE_DIR/release/verify-reproducible-build.sh"
actual_manifest="$(<"$DIST_DIR/SHA256SUMS")"
[[ "$actual_manifest" == "$EXPECTED_MANIFEST" ]] \
  || die "reproducible artifacts do not match the PiXi-validated manifest"

expected_signer="$(sed -n 's/^controller\.signer\.sha256=//p' \
  "$BASE_DIR/compat/pixi-private-pins.properties")"
actual_signer="$(tr -d '\r\n' < "$DIST_DIR/controller-cert-sha256.txt")"
[[ "$expected_signer" =~ ^[0-9a-f]{64}$ && "$actual_signer" == "$expected_signer" ]] \
  || die "candidate signer does not match the reviewed PiXi pin"

stage_root="$(mktemp -d)"
payload="$stage_root/$PAYLOAD_NAME"
bundle_tmp="$stage_root/$BUNDLE_NAME"
cleanup_stage() {
  [[ "$stage_root" == /tmp/* && -d "$stage_root" ]] && rm -rf -- "$stage_root"
}
trap cleanup_stage EXIT
mkdir -p "$payload/operator" "$payload/source"

install -m 0644 "$DIST_DIR/NullGate-prototype-debug.apk" \
  "$payload/NullGate-controller-pixi-private-0.2.0.apk"
install -m 0644 "$DIST_DIR/NullGate-theme-client-debug.apk" \
  "$payload/NullGate-theme-client-pixi-private-0.1.apk"
install -m 0644 "$DIST_DIR/NullGate-broker.jar" \
  "$payload/NullGate-broker-pixi-private-0.2.0.jar"
install -m 0644 "$DIST_DIR/controller-cert-sha256.txt" \
  "$payload/controller-cert-sha256.txt"
install -m 0644 "$BASE_DIR/release/PIXI_0.2.0_CANDIDATE.md" "$payload/README.md"
install -m 0644 "$BASE_DIR/STATUS.md" "$payload/STATUS.md"
install -m 0644 "$BASE_DIR/DEVICE_TEST_PASS_2026-09-28.md" \
  "$payload/DEVICE_TEST_PASS.md"
install -m 0644 "$BASE_DIR/REPRODUCIBLE_BUILD_2026-09-28.md" \
  "$payload/REPRODUCIBLE_BUILD.md"
install -m 0644 "$BASE_DIR/OPERATOR_GUIDE.md" "$payload/operator/OPERATOR_GUIDE.md"
install -m 0644 "$BASE_DIR/INSTALL_AND_ONBOARD.md" \
  "$payload/operator/INSTALL_AND_ONBOARD.md"
install -m 0755 "$BASE_DIR/device/nullgate-pixi" "$payload/operator/nullgate-pixi"
install -m 0755 "$BASE_DIR/device/nullgate-device-v2.sh" \
  "$payload/operator/nullgate-device-v2.sh"
install -m 0755 "$BASE_DIR/device/install-dolowolf-launcher.sh" \
  "$payload/operator/install-dolowolf-launcher.sh"
install -m 0644 "$BASE_DIR/device/NullGate PiXi.desktop" \
  "$payload/operator/NullGate PiXi.desktop"

commit="$(git -C "$BASE_DIR" rev-parse HEAD)"
branch="$(git -C "$BASE_DIR" branch --show-current)"
short_commit="${commit:0:12}"
source_name="NullGate-pixi-private-0.2.0-$short_commit.tar.gz"
git -C "$BASE_DIR" archive --format=tar HEAD | gzip -n -9 \
  > "$payload/source/$source_name"

{
  printf 'candidate=NullGate PiXi private 0.2.0 rc1\n'
  printf 'source_commit=%s\n' "$commit"
  printf 'source_branch=%s\n' "$branch"
  printf 'controller_version=0.2.0\ncontroller_version_code=3\n'
  printf 'theme_client_version=0.1\ntheme_client_version_code=1\n'
  printf 'signer_sha256=%s\n' "$actual_signer"
} > "$payload/BUILD_INFO.txt"

(cd "$payload" && find . -type f ! -name SHA256SUMS -print0 | sort -z \
  | xargs -0 sha256sum > SHA256SUMS)
find "$payload" -type f -exec touch -d "@$REPRODUCIBLE_EPOCH" -- {} +
(cd "$stage_root" && find "$PAYLOAD_NAME" -type f -print | LC_ALL=C sort \
  | zip -X -q "$bundle_tmp" -@)

mkdir -p "$OUTPUT_DIR"
cp -- "$bundle_tmp" "$OUTPUT_DIR/$BUNDLE_NAME"
chmod 0644 "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null || true
[[ "$(sha256sum "$bundle_tmp" | awk '{print $1}')" == \
    "$(sha256sum "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')" ]] \
  || die "candidate bundle hash changed during the final TerraDrive write"
(cd "$OUTPUT_DIR" && sha256sum "$BUNDLE_NAME" > "$BUNDLE_NAME.sha256")

echo "NullGate candidate staged; immutable releases directory was not touched."
echo "Bundle: $OUTPUT_DIR/$BUNDLE_NAME"
echo "Checksum: $OUTPUT_DIR/$BUNDLE_NAME.sha256"
