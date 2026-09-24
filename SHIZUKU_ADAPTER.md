# NullGate Shizuku compatibility candidate

## State

Host implementation and Android compilation are complete as an optional
compatibility experiment. It is registered only in the explicit
`COLORBLENDR_SHIZUKU_V1` broker mode. PiXi deployment has not occurred, and the
ordinary NullGate launcher action remains marker-only.

Reviewed inputs:

- official Shizuku release 13.6.0.r1086, package
  `moe.shizuku.privileged.api`;
- PiXi's installed ColorBlendr 3.0.1 (version code 42), package
  `com.drdisagree.colorblendr`;
- public certificate and APK digests recorded in `compat/pins.properties`.

PiXi's read-only inventory found ColorBlendr's Shizuku permission recorded as
granted while a renamed, modified Shizuku build (`Shi.bequiet`) owns the
permission group. NullGate does not trust or replace that package. The official
Shizuku installation attempt was rejected by Android before any change.

This experiment is not the product path. NullGate is self-contained; official
Shizuku remains an optional bridge for users who already choose it. The launcher
refuses renamed or modified Shizuku managers.

## Admission rules

The adapter refuses unless all of these are simultaneously true:

1. manager and target packages match their sole pinned current signers;
2. the target declares the Shizuku API permission;
3. no Shizuku server is running;
4. the target is not already authorized;
5. no other package holds the Shizuku permission.

It does not adopt a user's existing Shizuku session. That is deliberate: state
NullGate did not create cannot be honestly represented as a NullGate lease.

## Lifecycle

Activation grants only the fixed target permission, starts only the signed
manager's packaged native starter, and verifies one exclusive permission holder
plus a live root-owned `shizuku_server`. No caller text becomes a command.

Cleanup force-stops the target to drop live Binder access, stops the root-owned
server, revokes the target permission, and verifies both server and permission
absence. Partial activation is owned before the first mutation so failed
rollback remains retryable. Cleanup failure is retained by the broker and blocks
new leases.

## Limits

- This candidate is specific to the owner profile and currently pinned
  ColorBlendr build.
- SIGKILL is used for the Shizuku server because the manager-only graceful exit
  API is unavailable to the broker. Target force-stop precedes server teardown.
- A crash between mutations still requires host reconciliation; the candidate
  has not yet received the separate compatibility deployment/recovery harness.
- Shizuku may expose broad system APIs during the lease. Exclusivity and short
  lifetime reduce exposure; they do not turn Shizuku into a fine-grained API.
- Banking/integrity invisibility is neither claimed nor tested.

Host evidence: nine dedicated adapter tests and three broker-integration tests
plus the existing suites pass; the Android 36 APK and broker DEX compile and the
signed distribution verifies.
