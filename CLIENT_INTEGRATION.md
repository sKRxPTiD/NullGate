# NullGate client integration contract

## Purpose

NullGate is the privilege boundary. Client applications describe a narrowly
typed effect; they never receive root, a shell, a file descriptor, a Binder to
a root process, or caller-controlled command execution.

ColorBlendr is the first integration target, but the contract is product-wide.
The same flow can later support other signed clients and separately reviewed
capabilities.

## Trust path

1. A client sends an explicit request to the installed NullGate controller.
2. Android supplies the caller identity. NullGate resolves the UID to exactly
   one installed owner-user package and verifies its pinned signing certificate.
3. NullGate parses a versioned typed payload and rejects unknown fields,
   capabilities, enum values, durations, packages, or protocol versions.
4. NullGate shows the target, requested effect, and bounded lease window to the user.
   No privileged request executes before confirmation.
5. Only the signed NullGate controller talks to the ephemeral root broker.
6. The broker independently re-verifies the controller and enforces its own
   target, capability, duration, payload, and lifecycle policy.
7. Expiry, revoke, broker shutdown, partial activation failure, and lost reply
   all take the same verified cleanup path.

## Version 1 capability

`SYSTEM_THEME_SEED_APPLY`

Typed fields:

- `protocolVersion`: exactly `1`
- `seedArgb`: one opaque ARGB color
- `themeStyle`: one broker-defined enum value
- `durationMillis`: greater than zero and no more than ten minutes

The client cannot submit JSON, a settings key, a command, a path, an overlay
identifier, a user ID, or a package to mutate. NullGate generates the Android
theme record itself, snapshots the exact previous value in a root-owned receipt,
verifies activation, and restores the exact snapshot.

## ColorBlendr integration

The reviewed ColorBlendr 3.0.1 Shizuku and Wireless ADB paths ultimately update
`theme_customization_overlay_packages`. NullGate's proven native adapter already
owns that operation without Shizuku. A proper ColorBlendr integration therefore
adds a **NullGate** work method that sends only the selected seed and supported
style through this contract.

The preferred delivery is an upstream contribution. An internal PiXi build may
be used to validate the client contract first, but NullGate must never pretend
an unmodified ColorBlendr release supports it.

## Required controller work

- exported, explicit Android IPC endpoint with platform-provided caller UID;
- signer-pinned client registry with per-client capability scope;
- user-confirmation screen protected against obscured-touch approval;
- persisted pending/active/revoking state across Activity and process restart;
- correlated result and idempotent revoke token bound to the requesting UID;
- strict request-rate and outstanding-lease limits;
- automated spoofing, replay, malformed-payload, lifecycle, and cleanup tests.

The controller now contains the first implementation slice:

- an explicit request/revoke Activity that requires a result-bound caller;
- exact intent schemas with no additional fields;
- ColorBlendr UID, sole-package, and current-signer verification;
- a closed registry that binds each reviewed package, version, signer policy,
  and allowed capability;
- typed seed/style/duration parsing;
- an obscured-touch-protected confirmation screen;
- persisted uncertain-outcome recovery and caller-bound revocation;
- a standalone signer-pinning reference client under `client/reference/`.

The client reference compiles during every NullGate build. A host-only upstream
ColorBlendr patch now implements the work method, foreground result flow,
signer-pinned controller verification, revoke-before-replace, and explicit
revocation. It is saved under `integrations/patches/` and builds against the
reviewed 3.0.1 source. It has not been installed over ColorBlendr: a locally
signed APK cannot replace or impersonate the official package. Android
instrumentation and process-death testing require an officially signed upstream
release, followed by review and admission of that release version in NullGate.

`common/.../ExternalResultPolicy.java` is the canonical fail-closed result
policy for first-party clients. It accepts only exact result schemas, bounded
fresh grant receipts, operation-specific revoke confirmation, and explicit
clean reconciliation decisions. Everything else remains `UNKNOWN`.

For a new app or capability, start with `ADAPTER_AUTHORING_GUIDE.md` and record
the evidence in `UNIVERSAL_TEST_MATRIX.md`. ColorBlendr remains the first test
client, not a special privilege path.
