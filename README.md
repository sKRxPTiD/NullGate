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

## Current result — 2026-09-24

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

## Build and tests

Install JDK 17 and Android SDK Platform 36 with Build Tools 36.0.0, set
`ANDROID_HOME` when the SDK is not under `~/Android/Sdk`, then run
`./build.sh`. It compiles and tests the Java core, builds both Android
artifacts, verifies APK signatures and records SHA-256 checksums, then runs
simulated device-helper tests. Device tests now run after artifacts exist, so
the build no longer depends on a previous dist directory.

Current result: **135 host Java checks** (67 broker/security, 36 external-client
policy/state, and 32 test-client response/lifecycle checks) and the stateful device-helper scenario suite pass, including
additional final-review regressions.
These do not execute Android socket, SELinux, Activity lifecycle, app_process,
filesystem or shutdown behavior. No Android emulator/instrumentation test has
been run for this audit.

Outputs:
- `dist/NullGate-prototype-debug.apk`
- `dist/NullGate-test-client-debug.apk`
- `dist/NullGate-broker.jar`
- `dist/controller-cert-sha256.txt`
- `dist/SHA256SUMS`

The first local build generates a development-only signing identity under
`keys/`. Git ignores that directory. The controller and test client are signed
with that same local identity so each can reject an unpaired build. Keep the key
and password file together for repeat local builds, and never publish them.
Set `NULLGATE_KEY_DIR` to an existing private key directory when rebuilding an
already-installed paired prototype. Production distribution needs a separately
managed release identity and a fresh signer-policy review.

## Device safety gate

The reviewed marker and native-theme modes have passed supervised PiXi tests;
external-app compatibility and production use remain blocked.
The canonical `device/nullgate-device.sh` entry point requires the explicit
`NULLGATE_MARKER_TEST_V1` mutation token for every device write. Without it,
install, deploy, stop, cleanup and recovery stop before contacting ADB.

`device/nullgate-device.sh preflight` and `status` remain read-only on the
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

The controller now exposes a first typed client request/revoke Activity for the
reviewed ColorBlendr 3.0.1 package and a separate first-party NullGate Test
Client. It requires a result-bound Android caller, pins package, sole UID
ownership, version code and signer, rejects additional intent fields, shows an
obscured-touch-protected approval screen, persists uncertain outcomes, and
binds revoke to the original caller. The test client is a distinct package and
UID, is signed with the controller's local development identity, and receives
only a lease receipt. A signer-pinning reference client compiles with every
build. A direct ADB-shell spoof attempt on PiXi was denied with no broker
runtime and ADB remaining non-root. The matching ColorBlendr client patch now
builds off-device and is exported under `integrations/patches/`; PiXi's official
ColorBlendr remains untouched. Live ColorBlendr integration awaits upstream
adoption under the official signer and a new NullGate version-policy review.
See `CLIENT_INTEGRATION.md` and `TEST_CLIENT.md`.

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

See `OPERATOR_GUIDE.md` for the ordinary workflow. The ColorBlendr/Shizuku
candidate has not been deployed on PiXi. Its dedicated launcher action is
fail-closed until the modified-manager conflict is resolved and the pinned
official package is present.

## Integration and review

- `BROKER_CONTRACT.md` defines the root-side protocol and invariants.
- `CLIENT_INTEGRATION.md` defines the Android caller contract.
- `TEST_CLIENT.md` defines the first-party external-app harness and limits.
- `DEPLOYMENT_GATE.md` defines the supervised PiXi test and stop conditions.
- `NATIVE_CAPABILITY_CONTRACT.md` defines the first typed native capability.
- `THREAT_MODEL.md` and `SECURITY_AUDIT.md` record the current security boundary.
- `integrations/patches/` contains the reviewed ColorBlendr integration patch.

The upstream ColorBlendr proposal is tracked in
[Mahmud0808/ColorBlendr#314](https://github.com/Mahmud0808/ColorBlendr/pull/314).

## License and identity

The source is licensed under GPL-3.0-only; see `LICENSE`. The NullGate name,
chip-and-∅ mark, and associated product identity remain Null Protocol marks.
See `COPYRIGHT.md` for the branding boundary.
