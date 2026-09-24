# NullGate threat model — audit gate, 2026-09-24

## Objective versus current implementation

The intended product brokers narrow, authenticated, temporary capabilities.
The marker adapter is live-proven. A ColorBlendr-specific Shizuku-session
candidate now exists in the host build, but no Shizuku manager, compatibility
broker, app-callable su, or arbitrary command channel is installed by NullGate.

A lease deadline rejects late admission and triggers cleanup when the broker
runs. **It is not proof that a persistent effect disappears at an exact instant
during suspend, SIGKILL, kernel failure, or power loss.** The marker can outlive
a forcibly killed process. Startup refuses leftover markers rather than
silently adopting or deleting them.

## Trust boundary

Trusted: the owner, authorized host/rooted ADB, Android kernel peer credentials,
owner-user package-manager evidence, pinned local signing identity, broker and
registered adapters. Other applications and all request data are untrusted.

Root and ADB-shell adversaries are outside the current boundary. The runtime
is under /data/local/tmp, whose parent is shell-controlled; leaf lstat/ownership
checks do not defend against a hostile shell moving the parent. Client UID 0
verification rejects ordinary-app socket impersonation, not malicious root.

Only user 0 ordinary app UIDs with one exact package and one pinned current
signer are accepted. Shared UIDs, other profiles, isolated UIDs, multiple
signers and signing-key rotation are unsupported and denied.

## Controls implemented, with evidence limits

- Bounded typed protocol; no shell text or caller-provided filesystem paths.
- Exact capability allowlist, installed-package check and concrete adapter.
- Single engine lock across activation, expiry/revoke cleanup and shutdown.
- Negative/overflowing timestamp rejection; monotonic deadline rechecked after
  activation; nonce and lease history bounded to 256 entries per process.
- Revoke-before-issue tombstones; terminal shutdown; successful cleanup receipts.
- Cleanup failures retain records and block further admissions until restart and
  reconciliation. Adapters must support retryable, idempotent cleanup.
- Peer-root check and correlated operation-specific replies at the controller.
- Durable controller recovery record before transmit; serialized requests;
  ambiguous replies and NOT_FOUND remain unconfirmed, not asserted safe.
- Runtime root-only leaf directories; exclusive no-follow marker/PID creation;
  stale marker refusal; shutdown hook plus elapsed-clock watchdog.
- Signing files mode 0600, containing directory mode 0700; incomplete key pair
  fails rather than generating a replacement identity.

Pure Java controls have host tests. Android controls compile but are not
live-validated. Root-side audit output is checked for I/O failure on decisions,
but is not an fsync-backed or tamper-evident audit store. Controller recovery
and rotation behavior still require instrumentation.

## Remaining blockers

1. Run the replacement harness in a narrow marker-only window. It now requires
   a pinned device serial, installed APK signer verification, artifact digest
   checks, safe directory ownership, robust process identity, preserved logs,
   and explicit recovery for validated marker leftovers.
2. Verify Android hidden ActivityThread/package-manager behavior, SELinux socket
   policy, runtime file access and shutdown on the exact LineageOS build.
   Do not disable SELinux, install permanent su, or spoof root detection to
   bypass incompatibility.
3. Verify controller recreation, process death, offline submit/revoke, duplicate
   clicks, socket squatting and replies lost before/after activation.
4. Exercise marker revoke, expiry, suspend/resume, disconnect, SIGTERM and
   forced kill; distinguish confirmed cleanup from artifacts requiring recovery.
5. Before the external-app capability, finish the dedicated compatibility
   deployment/recovery harness and test the implemented signer binding,
   exclusive authorization, target force-stop, server teardown and permission
   revocation on PiXi. A global Shizuku root service is not automatically a
   per-app expiring lease.

## Operational gate

No automatic device writes in build or tests. PiXi initially had ordinary UID
2000 ADB with SELinux Enforcing. After Wolf enabled rooted debugging and gave
permission, a brief rooted ADB session verified UID 0 / u:r:su:s0 / Enforcing.
The runtime directory and controller package were absent. ADB was returned to
non-root after inspection; the Android rooted-debugging toggle is user-controlled.

The final host review permits the narrow marker experiment under Wolf's existing
authorization. Before device writes, verify current backups/recovery readiness
and enable rooted ADB only for that test. Use one administrative host/operator;
the helper's host lock cannot coordinate other hosts or manual ADB commands.
External-app capabilities remain disabled during marker validation. Reconcile
all artifacts and processes before closing the root session.

Custom ROM, unlocked bootloader, root/debugging and Shizuku can still affect
banking/integrity. The aim is reduced persistent exposure, never guaranteed
invisibility.

## Platform references

Android documents that elapsedRealtime includes deep sleep, while sleep and
Handler scheduling use uptime. A sleeping process cannot guarantee on-time
teardown merely by choosing a monotonic clock:
[SystemClock](https://developer.android.com/reference/android/os/SystemClock).

The socket credential API supplies the connected peer's credentials:
[LocalSocket.getPeerCredentials](https://developer.android.com/reference/android/net/LocalSocket#getPeerCredentials()).
