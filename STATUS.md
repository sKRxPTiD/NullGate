# NullGate status — 2026-09-28

## PiXi private MVP: complete

NullGate 0.1.0 is installed on PiXi as a matched private deployment. The typed
system-theme capability has passed real grant, explicit revoke, automatic
60-second expiry, exact restoration, broker shutdown and runtime removal with
the private ColorBlendr fork. The controller and client identities are pinned.
The ordinary idle state is non-root ADB with no broker runtime.

## Active source: 0.2.0 PiXi-validated candidate

The active source has advanced to version code 3 for the second first-party
typed client. This does not replace or revise the immutable 0.1.0 release
baseline. The signed host build, policy tests, lifecycle tests, artifact
checksums and simulated device-helper suite pass. The reviewed controller and
theme-client artifacts also passed their supervised PiXi installation,
grant, explicit-revoke, natural-expiry, exact-restoration, reconciliation and
shutdown gate on 2026-09-28.

See `DEPLOYMENT_REVIEW_2026-09-28.md` for the remediation review and
`DEVICE_TEST_PASS_2026-09-28.md` for the resulting live evidence. This is a
validated development candidate, not a new release promotion.

ColorBlendr is the first client, not NullGate's architecture. New clients use
the same broker contract, identity checks, lease rules and audit decisions, but
each new capability still needs a narrow adapter and its own restoration test.

## What is not required to use the PiXi MVP

- The upstream ColorBlendr maintainer does not need to merge the proposal.
- Shizuku is not required for the native theme capability.
- Astra is not required for routine builds, documentation or normal operation.
- No permanent app-callable `su` is installed.

## Remaining product work

1. Stabilization: use the current matched build and preserve any failure logs.
2. Release engineering: replace the local development signing identity with a
   deliberately managed release identity before public distribution.
3. Resilience: power loss, kernel failure or a forcibly killed broker can delay
   cleanup; the guarded host recovery path remains part of the safety model.
4. Generalization: the second first-party typed client now passes both its host
   and PiXi device gates. NullGate never grants a generic shell just because one
   client passed.
5. Public product work: installer, user-facing onboarding, release support and
   broader device/ROM testing are separate from the working PiXi deployment.

## Optional public path

The upstream ColorBlendr proposal is useful for official-signer adoption but
is not a dependency of the private PiXi deployment. Waiting for its maintainer
does not block NullGate development or use.

## Current operating gate

Normal sessions start from DoloWOLF's **NullGate · PiXi** launcher. Rooted
debugging is enabled only for a session and switched off after guarded Stop.
An UNKNOWN state is never treated as clean; use the launcher recovery path and
preserve evidence instead of manually deleting the runtime.

The subsequent host recovery hardening verifies snapshot transfers and archived
receipts/logs, preserves unique evidence files, and rechecks the theme receipt
before removal. The simulated failure regressions pass; see
`RECOVERY_HARDENING_2026-09-28.md`. The installed launcher uses this source
helper directly. These follow-up changes have not been live-tested on PiXi
and are not included in the preserved RC1 bundle.

The DoloWOLF launcher was refreshed from tracked source after the 0.2.0 live
pass. Its read-only doctor verifies the installed controller code 3, theme
client code 1, admitted ColorBlendr code 42001, all expected signers, ordinary
ADB and an absent managed runtime. Separate launcher actions now open either
ColorBlendr or the first-party Theme Client through the typed system-theme
broker.

The hardened Theme Client build was subsequently installed and revalidated on
PiXi. Its selected palette/style now survive theme-driven Activity recreation,
and natural expiry displays specific reconciliation guidance without treating
elapsed local time as cleanup proof. Exact restoration, reconciliation and
final zero-runtime shutdown passed again.

The paired build is now byte-reproducible across consecutive clean signed host
builds; see `REPRODUCIBLE_BUILD_2026-09-28.md`. The exact deterministic
controller, theme-client and broker artifacts subsequently passed their own
supervised PiXi grant/revoke/expiry/restoration gate. They are eligible for a
separately reviewed 0.2.0 candidate bundle. The resulting RC1 bundle was staged
and audited successfully; see `CANDIDATE_STAGE_2026-09-28.md`. Its ZIP and
checksum sidecar were then copied byte-for-byte into the authoritative
`releases/` directory without modifying the verified 0.1.0 baseline; see
`RELEASE_PROMOTION_2026-09-28.md`. RC1 remains a private release candidate, not
a final public release.
