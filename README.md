# NullGate

**Security research prototype — not a production root solution.**

NullGate is Null Protocol's typed, temporary-privilege broker for Android. It
is designed to grant a narrowly defined capability for a short lease, verify
the caller, expire or revoke the lease, restore prior state, and remove its
root-side runtime. It deliberately does not install a general-purpose `su`
binary or expose an arbitrary command executor.

Null Protocol → PiXi → NullGate is the first deployment. The architecture is
client-neutral; ColorBlendr is the first integration target, not a special
case baked into the broker.

The controller uses green circuit branding and the ∅ mark over a cappuccino
palette.

## Current result — 2026-09-28

The signed controller and separate Android broker build locally. The marker
lease and native 60-second theme lease have both passed supervised PiXi tests,
including exact restoration and clean runtime removal. **No external app has
received root access.** A candidate exclusive Shizuku-session adapter exists
for ColorBlendr behind a separate explicit broker mode and release gate.

The broker authenticates the controller using socket UID, sole package
ownership, and a pinned sole current signer. Only owner-user ordinary app UIDs
are supported. The client checks a root peer and validates response correlation.
Root identity is not proof against another compromised root process.

Activation and cleanup are serialized. Expired activation is rolled back,
cleanup failures remain tracked and block new admissions, and completed cleanup
receipts support repeat revoke. Controller intent is saved before sending.
Lost replies, restart and unknown broker records are not presented as no root.

The local log is bounded to 16 KiB; it is not a tamper-proof audit ledger.
A marker is a lifecycle test, not proof of reversible privilege in other apps.

The immutable verified release baseline remains NullGate 0.1.0 (version code
2). PiXi now runs the active 0.2.0 development controller (version code 3) and
first-party theme client after their supervised device gate passed. See
`STATUS.md` and `DEVICE_TEST_PASS_2026-09-28.md` for the boundary between the
release baseline and validated development state.

The private 0.2.0 RC2 bundle was subsequently audited and promoted under
`releases/`; it updates the host recovery helper and operator guidance while
retaining the exact PiXi-validated Android artifact hashes. It is not a public
release, and the immutable 0.1.0 baseline remains unchanged. See
`RELEASE_PROMOTION_RC2_2026-09-28.md`. A later DoloWOLF desktop-shortcut fix is
in active source but was not retroactively added to the immutable RC2 bundle.
Active source also fixes theme recovery when an interrupted broker leaves a
validated lease marker; this has simulated coverage and awaits supervised live
recovery validation before a future candidate bundle.

## Build and tests

Install JDK 17 and Android SDK Platform 36 with Build Tools 36.0.0, set
`ANDROID_HOME` when the SDK is not under `~/Android/Sdk`, and provide the
matched existing `nullgate-local.keystore` plus `.nullgate-local.pass` through
`NULLGATE_KEY_DIR` and `NULLGATE_KEYPASS_FILE` when they are not under `keys/`.
Then run `./build.sh`. It compiles and tests the Java core, builds all Android
artifacts, verifies APK signatures and records SHA-256 checksums, then runs
simulated device-helper tests. Device tests run after artifacts exist, so the
full build no longer depends on a previous dist directory.

Generated archive inputs use a fixed timestamp, compiler inputs are sorted,
transient ZIP metadata is stripped, and APKs explicitly use deterministic
v2/v3 signing without the unused v1/JAR scheme. Repeated builds from identical
source, tools and signing identity must therefore produce identical artifact
hashes.

Run `release/verify-reproducible-build.sh` for a two-clean-build comparison.
The latest verified manifest and the boundary between host reproducibility and
device validation are recorded in `REPRODUCIBLE_BUILD_2026-09-28.md`.

After the exact deterministic set passes its device gate, use
`release/package-pixi-0.2.0-candidate.sh` to stage the reviewed private upgrade
candidate under ignored `dist/release-candidate/`. The packager refuses dirty
tracked state, unexpected untracked files, hash drift and signer drift. It does
not write into the authoritative `releases/` directory.

Use `./build.sh --preflight` to validate dependencies, release metadata, and
the selected signing pair without changing files. Use `./build.sh --host-only`
for the same host compilation, artifact, signing, metadata, and checksum
verification without the simulated device-helper suite. The local development
signing identity is never generated implicitly; `./build.sh
--init-dev-signing` is the deliberate bootstrap command when both development
signing files are absent.

Current result: **199 host Java checks** (74 broker/security, 76 external-client
policy/state, 34 test-client response/lifecycle, and 15 theme-client lifecycle
checks) and the stateful device-helper scenario suite pass, including additional
final-review regressions.
These do not execute Android socket, SELinux, Activity lifecycle, app_process,
filesystem or shutdown behavior. No Android emulator/instrumentation test has
been run for this audit.

The subsequent deployment review exercised real non-root Android approval,
denial, process restart, reconciliation and broker-absent behavior on PiXi.
See `DEPLOYMENT_VERDICT_2026-09-25.md` for the evidence, remaining limits, and
conditional approval of the supervised first-party external-client theme test.
That test subsequently stopped safely on theme-triggered Activity recreation:
the grant was revoked and exact restoration verified. The lifecycle repair is
recorded in `DEVICE_TEST_2026-09-25.md`.
The bounded repair retains one authenticated operation across configuration
recreation while leaving process-death recovery fail-closed. The reviewed
rerun passed real grant delivery, immediate revoke, natural expiry, exact theme
restoration and clean shutdown. See `DEVICE_TEST_PASS_2026-09-25.md` for the
current first-party test result. The PiXi private ColorBlendr fork also passed
grant, immediate revoke, automatic expiry and exact restoration; arbitrary
third-party compatibility remains unverified.

Outputs:
- `dist/NullGate-prototype-debug.apk`
- `dist/NullGate-test-client-debug.apk`
- `dist/NullGate-theme-client-debug.apk`
- `dist/NullGate-broker.jar`
- `dist/controller-cert-sha256.txt`
- `dist/SHA256SUMS`

A deliberately initialized local build can create a development-only signing
identity under `keys/`; Git ignores that directory. Ordinary builds require an
existing matched keystore and password file and never replace either one. The
controller and test client are signed with that same identity so each can reject
an unpaired build. Keep the encrypted keystore private and keep its plaintext
password file on protected local storage. Set `NULLGATE_KEY_DIR` and
`NULLGATE_KEYPASS_FILE` to explicit existing paths when rebuilding an
already-installed paired prototype. Production distribution needs a separately
managed release identity and a fresh signer-policy review.

On DoloWOLF, `release/build-pixi-private.sh` selects the protected paired PiXi
credential paths, verifies the reviewed signer pin, and forwards build options.
Run `release/build-pixi-private.sh --preflight` for a read-only readiness check
or add `--host-only` for a host artifact build without the simulated device
helper suite.

## Device safety gate

The reviewed marker and native-theme modes and the pinned private ColorBlendr
client have passed supervised PiXi tests; production use remains unapproved.
The canonical `device/nullgate-device-v2.sh` entry point requires the explicit
mode token for every device write. Without the matching token, install, deploy,
stop, cleanup and recovery stop before contacting ADB.

`device/nullgate-device-v2.sh preflight` and `status` remain read-only on the
device and pin all ADB calls to one serial. Set `NULLGATE_SERIAL` explicitly
for every mutation, even with only one device attached. A per-host lock prevents
overlapping helper operations on the same serial. Preflight requires tokay, Android 16,
LineageOS 23.2, an active root ADB session, the expected SELinux domain and
Enforcing mode, then verifies the installed controller signer. Status
distinguishes unknown state from verified process identity. `recover-marker-runtime`
only removes a stale PID and a marker whose owner, mode, filename and exact
record format all validate; it refuses every other leftover.

The initial 2026-09-24 read-only check found UID 2000 ADB with SELinux Enforcing.
After Wolf enabled rooted debugging and authorized proceeding, a brief root ADB
session verified UID 0, u:r:su:s0 and Enforcing mode. The controller package and
runtime directory were absent. ADB was then returned to non-root. No APK was
installed, broker launched, device file removed or SELinux setting changed.
The rooted-debugging toggle remains user-controlled.

The narrow marker-only PiXi run completed successfully on 2026-09-24: immediate revoke and
60-second expiry both reconciled to zero markers, shutdown and cleanup completed,
and ADB returned to non-root. The controller UI now implements the approved
cream/cappuccino NullGate treatment with green circuit traces, chip/null mark,
status card, brown controls and framed local audit log. The controller remains
installed on PiXi; the root runtime is absent.

The optional compatibility experiment pins the reviewed ColorBlendr 3.0.1
installation and official Shizuku 13.6.0 signer. It is registered only in the
explicit `COLORBLENDR_SHIZUKU_V1` broker mode. PiXi contains a renamed, modified
Shizuku build (`Shi.bequiet`) that owns the Shizuku permission group. NullGate
neither trusts nor replaces it, and the launcher refuses compatibility mode
while that package is present.
The official installation attempt failed before changing the device.

The product path is now explicitly self-contained: typed NullGate capabilities
and an integration contract for clients. Shizuku is optional, not required.

The controller exposes a typed client request/revoke Activity for the reviewed
ColorBlendr 3.0.1 package, the first-party NullGate Test Client, and a second
first-party NullGate Theme Client. The theme client offers closed palette/style
choices through the same bounded capability; it is not a shell or arbitrary
settings interface. A closed client registry keeps package, display name,
version, signer source, and allowed capability together instead of embedding
client-specific trust branches in the request parser. It requires a result-bound
Android caller, pins package, sole UID ownership, version code and signer,
rejects additional intent fields, shows an
obscured-touch-protected approval screen, persists uncertain outcomes, and
binds revoke to the original caller. Both first-party clients are distinct
packages and UIDs, are signed with the controller's selected identity, and
receive only lease receipts. A signer-pinning reference client compiles with every
build. The 0.2.0 controller and theme client passed their signed host build,
simulated device-helper suite and supervised PiXi
grant/revoke/expiry/restoration gate. A direct ADB-shell spoof attempt on PiXi
was denied with no broker
runtime and ADB remaining non-root. The matching ColorBlendr client patch now
builds off-device, rejects malformed or ambiguous Activity results, and
reconciles interrupted grant/revoke operations instead of treating them as
clean. Its policy unit tests pass and the upstream-oriented patch series is
exported under `integrations/patches/`. On this private branch, PiXi now runs
the pinned `v3.0.1-nullgate.1` fork and its live lease lifecycle passed.
Upstream adoption under the official signer remains a separate public path.
See `CLIENT_INTEGRATION.md`, `TEST_CLIENT.md`, and `THEME_CLIENT.md`.

Protocol v2 and the first native adapter are now implemented off-device. The
typed `SYSTEM_THEME_SEED_APPLY` capability accepts only an opaque color and an
enumerated style, uses Android's fixed settings utility without exposing a
general shell, snapshots the exact prior theme value, and verifies exact
restoration. It is gated behind the
explicit broker mode `SYSTEM_THEME_V1`; the existing marker launcher cannot
enable it. See `NATIVE_CAPABILITY_CONTRACT.md`.

See `SECURITY_AUDIT.md`, `THREAT_MODEL.md`, `BROKER_CONTRACT.md`,
`COLORBLENDR_ADAPTER.md` and `SHIZUKU_ADAPTER.md` for implementation boundaries
and evidence.

## Running the guarded prototype

The versioned device entry point is `device/nullgate-device-v2.sh`. It pins the
target serial, checks the controller signer and platform identity, refuses
unsafe runtime states, and never force-kills a broker or hides an `UNKNOWN`
result. Device mutations require both an explicit serial and the documented
mode token; read-only `preflight` and `status` remain available without it.

Rooted debugging is still a user-controlled test-window switch. Enable it on
PiXi before Start, Stop, or Recovery. A clean Stop archives the broker log,
verifies zero leases, removes the managed runtime, returns ADB to non-root, and
reminds the operator to disable Rooted debugging on the phone.

See `OPERATOR_GUIDE.md` for the ordinary workflow. On the PiXi private branch,
the pinned ColorBlendr fork's typed system-theme grant and explicit revoke path
passed on-device. This path does not require Shizuku.

## Integration and review

- `BROKER_CONTRACT.md` defines the root-side protocol and invariants.
- `CLIENT_INTEGRATION.md` defines the Android caller contract.
- `TEST_CLIENT.md` defines the first-party external-app harness and limits.
- `THEME_CLIENT.md` defines the second first-party typed client and its gate.
- `DEPLOYMENT_GATE.md` defines the supervised PiXi test and stop conditions.
- `INSTALL_AND_ONBOARD.md` covers DoloWOLF launcher setup and the first read-only check.
- `NATIVE_CAPABILITY_CONTRACT.md` defines the first typed native capability.
- `THREAT_MODEL.md` and `SECURITY_AUDIT.md` record the current security boundary.
- `integrations/patches/` contains the reviewed ColorBlendr integration patch.
- `ADAPTER_AUTHORING_GUIDE.md` and `UNIVERSAL_TEST_MATRIX.md` guide future
  clients and capabilities.

The upstream ColorBlendr proposal is tracked in
[Mahmud0808/ColorBlendr#314](https://github.com/Mahmud0808/ColorBlendr/pull/314).

The `pixi-private-colorblendr` branch is a separate personal deployment gate
for the disposable PiXi fork. It does not alter the official-signer policy on
`main`; see `PIXI_PRIVATE_COLORBLENDR.md` on that branch.

## License and identity

The source is licensed under GPL-3.0-only; see `LICENSE`. The NullGate name,
chip-and-∅ mark, and associated product identity remain Null Protocol marks.
See `COPYRIGHT.md` for the branding boundary.
