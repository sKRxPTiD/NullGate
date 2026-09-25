# NullGate broker contract — audited prototype

## Product boundary

Null Protocol → PiXi → NullGate. A device-neutral temporary capability broker,
with PiXi-specific platform integration. ColorBlendr is the first external
target, not the definition of the product.

The controller is unprivileged. A separate rooted-ADB app_process broker
accepts ISSUE and REVOKE on abstract local socket nullgate-broker-v1.
No command strings, generic shell execution, URI or arbitrary path fields exist.

## Authentication and admission

The broker obtains caller UID from kernel socket credentials and checks owner
user ordinary app UID, sole ownership of org.nullprotocol.nullgate, and one
exact pinned current signing certificate. Request fields cannot supply identity.
The controller checks peer UID 0 and correlates lease ID and operation-specific
response codes. Root processes remain trusted.

ISSUE fields: random lease ID and nonce, exact target package, named capability,
issued and expiry elapsedRealtime timestamps. Times must be nonnegative, live
and within the ten-minute maximum. Nonces/IDs cannot be reused. Capacity is
256 IDs per process, including revoke-before-issue tombstones. The production
policy currently permits exactly one active lease; the ceiling is explicit and
must be reviewed before any future increase.

Target installation and a registered ready capability adapter are required.
There are no production convenience constructors that supply a no-op adapter.

## Lifecycle and responses

- GRANTED follows completed activation and a fresh deadline check.
- Activation that fails or finishes after expiry triggers cleanup.
- Activation, revoke, expiry and shutdown are serialized. Future adapters must
  be bounded and non-reentrant; an indefinitely blocking adapter is unsupported.
- CLEANUP_FAILED is not success. Keep the exact adapter/lease for cleanup retry
  and block new admissions. Do not restart blindly to clear the fault.
- REVOKED means this broker completed cleanup, or retains a receipt that it did.
- NOT_FOUND means this broker lacks an activation/cleanup record. It does not
  prove absence of artifacts from a previous broker instance.
- An authenticated revoke before ISSUE prevents the delayed ISSUE from granting.
- Shutdown permanently closes admission and attempts all tracked cleanup.
- Failed audit/reply output after grant triggers a revoke attempt. Lost replies
  remain ambiguous to the client because cleanup can itself fail.
- Controller persists intent before send and does not resend ISSUE on recreation;
  it requests revoke. No local timer declares remote cleanup successful.

## Marker-only implementation

The only adapter creates a bounded mode-0600 marker for the controller package
under /data/local/tmp/nullgate/leases. It removes only files that its own
instance created. Runtime leaf directories must be root-owned mode 0700; PID and
marker creation is exclusive/no-follow. Startup refuses stale marker contents.
Root and ADB-shell administrative interference are outside this trust model.

A 250 ms uptime wake loop checks elapsed deadlines and accepted-socket elapsed
timeouts. This is best-effort scheduling, not a real-time or suspend guarantee.
Normal shutdown has a synchronized cleanup hook. SIGKILL/power loss cannot run
Java cleanup; marker/JAR/log leftovers require host reconciliation. The broker
has a fifteen-minute elapsed deadline but cannot promise to exit while the CPU
is asleep.

## Release status

The canonical device harness now gates install/deploy/stop/cleanup/recovery on
one pinned serial, artifact and installed-signer checks, and the explicit
marker-test mutation token. Host tests and an Android build are still not PiXi
integration proof; deployment remains a separate marker-only release gate.
The candidate Shizuku adapter exists in the host build but remains outside the
marker-only device gate. Universal scope means an extensible architecture, not
compatibility with every root app.

The candidate adapter pins Shizuku and target signing certificates, requires
the target's Shizuku permission declaration, refuses pre-existing server/grant
state, allows exactly one permission holder, and proves server/permission
absence on cleanup. It still needs a separate supervised Android release gate.
Root-backed Shizuku is not an integrity bypass or an automatic lease boundary.
