# NullGate security audit — 2026-09-24

## Decision

**Marker experiment passed. The Shizuku compatibility candidate remains NO-GO
for PiXi until its dedicated deployment/recovery gate and live review pass.
Production use remains NO-GO.**
This is a scoped engineering review under a trusted, single administrative
host/operator assumption, not an independent audit. Host-side hardening is
implemented and tested. This audit is not a security
certification. No NullGate software was installed or launched on PiXi.

## Findings and changes

| Finding | Change / status |
|---|---|
| Activation ran outside the engine lock and could finish after revoke/expiry | Adapter activation and cleanup now share the engine lock; late activation is rolled back. |
| Cleanup errors were swallowed and state removed before teardown | Failed cleanup stays tracked; CLEANUP_FAILED blocks new grants; retries retain the exact adapter. |
| Shutdown did not close admission; early revoke could be overtaken by ISSUE | Terminal closed state and bounded revoke tombstones added. |
| Lost reply made controller assert no privilege | Durable recovery record, serialized transport, unconfirmed UI and rollback attempt on failed reply/audit output. |
| Controller trusted any abstract-socket server and any response ID | Root peer check, lease correlation and operation-specific response validation. |
| Shared UID/multiple signers/other users ambiguously accepted | Single owner-user app UID, exact sole package and signer; strict digest validation. |
| Negative issue time could overflow duration arithmetic | Negative issue timestamps rejected before subtraction. |
| Production session constructors silently provided ready no-op adapters | Removed; fake adapters exist only in host-test sources. |
| Marker/PID writes followed unsafe preexisting paths | Root-owned private leaf checks, exclusive/no-follow creation, own-file cleanup, stale artifact refusal. Parent remains trusted-shell boundary. |
| Shutdown depended on main finally; lifetime used one long uptime sleep | Cleanup hook, serialized cleanup completion, elapsed watchdog and accepted-socket deadline; no force-kill/suspend guarantee. |
| Old helper trusted any live PID, suppressed cleanup errors, and could overwrite runtime files | Canonical helper now pins serial, verifies installed signer, hashes the broker, validates root process identity, preserves logs, and reports UNKNOWN accurately. Mutations require an explicit marker-test token. |
| Build required dist before rebuilding it; incomplete signing pair could be silently replaced | Device simulations moved after artifact production; incomplete key pair fails; key directory private. |
| Old documents claimed every teardown path and full hard expiry | Replaced with explicit tested behavior, limitations and deployment gates. |

## Verification

- 57 Java tests: policy 7, protocol 7, caller identity 4, target gate 3,
  lifecycle 3, marker record 3, Shizuku adapter 9, Shizuku/broker integration 3,
  native system-theme adapter 5, security regressions 13.
- Stateful fake-ADB scenarios: signer/install, launch/hash, process identity,
  stop, cleanup, stale-PID recovery, marker validation and mutation gate.
  These are simulations, not real Android deploy/stop/cleanup tests.
- Six additional final-review regressions pass: running broker without a PID
  receipt, running broker without a runtime directory, failed process inventory,
  failed recovery process inspection, reused PID, and failed stop inspection.
  Invalid-marker recovery also verifies that its PID receipt remains intact.
- Android 36 compilation, signed APK verification, broker DEX packaging and
  SHA-256 manifest verification.
- Live read-only PiXi evidence: Pixel 9/tokay connected; preflight correctly
  stopped at non-root ADB; UID 2000, context u:r:shell:s0, SELinux Enforcing.
- Follow-up authorized by Wolf after enabling rooted debugging: adb root
  succeeded; verified UID 0, u:r:su:s0, Enforcing, absent runtime directory and
  no installed controller package. Returned ADB to non-root after inspection.
- No installation, Android toggle change by the agent, broker launch,
  device file deletion or bank/ColorBlendr interaction.

## Unfinished release gates

### Final helper review

Recovery no longer interprets failed process inspection as proof of exit.
It validates all marker records and directory entries before removing recovery
evidence. Deployment, cleanup, recovery and clean-state verification check the
process inventory independently of PID receipts. Stop requires a successful
explicit absent-process result; inspection failures remain UNKNOWN. Remote
inventory failures are no longer hidden behind a successful sort command.
Mutations require an explicit serial and a per-host, per-serial lock. Installed
APK verification rejects multiple signer digests rather than reading only one.
The lock does not coordinate another host or manual ADB commands: do not run
other administrative operations during the test.

Final host rerun: all 38 Java tests, the device simulation suite, helper shell
syntax checks and all three distribution checksums pass. These helper-only
changes do not alter the packaged APK or broker. No device operation was run
during this final review.

The next experiment may install the controller and launch only the marker
broker. Confirm current recovery readiness before writes, then verify controller
connection, one marker lease, revoke, expiry, stop and clean-state reconciliation.
Stop on any UNKNOWN result; preserve logs and receipts. Return ADB to non-root
after reconciliation; disable rooted debugging when the test window ends.
Do not enable an external-app adapter or treat this verdict as proof that
Android runtime behavior has already passed.

See THREAT_MODEL.md. The replacement deployment/recovery harness now passes
stateful simulation, but it still needs a narrow marker-only PiXi run to test
Android-specific package signing, app_process, local sockets, controller
recovery and shutdown behavior.
Suspend, abrupt kill and app lifecycle remain unverified.

The current controller intentionally retains unresolved NOT_FOUND state after
broker restart: it cannot certify cleanup for a process whose records are gone.
A reviewed host-reconciliation/reset flow is still required. Do not clear app
data or suppress this warning merely to make the UI look ready.

ColorBlendr has no enabled adapter on PiXi. The host build now contains a
separate, signer-pinned `SHIZUKU_SESSION_START` candidate with exclusive clean
preflight, fixed operations, target force-stop, server stop, permission revoke,
post-cleanup proof and retryable partial activation. It still needs a separate
device harness and live gate. Passing host tests does not deliver temporary
privilege to ColorBlendr or another application.

The self-contained product path now has protocol-v2 typed payloads and a native
system-theme seed adapter. It accepts no arbitrary JSON or commands, preserves
unrelated theme keys, snapshots/restores the exact prior setting and is disabled
unless the broker receives the explicit `SYSTEM_THEME_V1` mode. This is host and
Android-build evidence only; it has not changed PiXi's theme.

## Supervised PiXi marker experiment — 2026-09-24

The signed controller and marker-only broker were exercised on PiXi with UID 0,
`u:r:su:s0` and SELinux Enforcing. One lease was granted and immediately revoked;
its root-owned mode-0600 marker disappeared. A second lease remained present
until its 60-second elapsed-time deadline, after which the controller received
`REVOKED` and the marker directory was empty.

The first live launch exposed two implementation defects. The custom brand view
measured itself to the full window and hid the controls; its height is now
bounded. Android `app_process` exited on SIGTERM without removing the PID
receipt; the helper now removes that receipt only after independently proving
the recorded process absent, finding no broker process, validating the receipt,
and confirming zero lease markers. Both changes pass the host regression suite.

The final stop completed, the broker log was preserved, the managed runtime was
removed, ADB returned to UID 2000 / `u:r:shell:s0`, and SELinux remained
Enforcing. The visually revised controller remains installed; no broker,
lease marker, permanent `su`, ColorBlendr adapter or Shizuku adapter remains
active.
