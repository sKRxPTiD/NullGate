# NullGate status — 2026-09-28

## PiXi private MVP: complete

NullGate 0.1.0 is installed on PiXi as a matched private deployment. The typed
system-theme capability has passed real grant, explicit revoke, automatic
60-second expiry, exact restoration, broker shutdown and runtime removal with
the private ColorBlendr fork. The controller and client identities are pinned.
The ordinary idle state is non-root ADB with no broker runtime.

## Active source: 0.2.0 PiXi-validated private RC2

The active source has advanced to version code 3 for the second first-party
typed client. This does not replace or revise the immutable 0.1.0 release
baseline. The signed host build, policy tests, lifecycle tests, artifact
checksums and simulated device-helper suite pass. The reviewed controller and
theme-client artifacts also passed their supervised PiXi installation,
grant, explicit-revoke, natural-expiry, exact-restoration, reconciliation and
shutdown gate on 2026-09-28.

See `DEPLOYMENT_REVIEW_2026-09-28.md` for the remediation review,
`DEVICE_TEST_PASS_2026-09-28.md` for the resulting live evidence, and
`RELEASE_PROMOTION_RC2_2026-09-28.md` for the audited private RC2 promotion.
The 0.1.0 ZIP remains the immutable baseline; RC2 is a private candidate, not a
public release.

ColorBlendr is the first client, not NullGate's architecture. New clients use
the same broker contract, identity checks, lease rules and audit decisions, but
each new capability still needs a narrow adapter and its own restoration test.

## What is not required to use the PiXi MVP

- The upstream ColorBlendr maintainer does not need to merge the proposal.
- Shizuku is not required for the native theme capability.
- Astra is not required for routine builds, documentation or normal operation.
- No permanent app-callable `su` is installed.

## Remaining non-blocking work

The supervised interrupted-theme recovery rehearsal passed exact restoration,
archival, guarded controller-record recovery and client reconciliation. It
exposed a stale countdown/disabled-Reconcile UI defect; its signed source fix
passed all 199 host checks, nineteen launcher regressions, the simulated helper
suite and live foreground-expiry/reconciliation/cleanup validation. The private
PiXi acceptance pass is complete. ADB is ordinary and the runtime is absent;
Wolf must turn off the Rooted debugging toggle separately.
See `LIVE_RECOVERY_REHEARSAL_2026-09-28.md`.
Real theme leases create a snapshot, not an ephemeral lease marker;
the earlier claim of a marker-related theme defect was not supported by the
adapter implementation. Unexpected mixed evidence remains fail-closed. Do not
manufacture a receipt or interrupt a broker outside the planned test window.

Public distribution is a separate project: it needs a deliberately managed
release signing identity, a fresh signer-policy review, broader device/ROM
coverage, and any desired official-signer ColorBlendr adoption. None blocks the
private PiXi workflow. Power loss, kernel failure, or a forcibly killed broker
can delay cleanup; preserve evidence and use guarded host recovery.

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
helper directly. A supervised PiXi broker start/stop with no lease passed log
hash verification, cleanup and return to ordinary ADB with an unchanged theme.
Theme-receipt recovery subsequently passed its supervised live rehearsal.
Concurrent-change and failure branches remain simulated. These follow-up changes
were excluded from RC1. RC2 is now packaged and promoted with the hardened
helper, updated beginner guidance, and the same PiXi-validated Android
artifacts. See `RELEASE_PROMOTION_RC2_2026-09-28.md`.
The post-RC2 marker-cleanup extension was withdrawn during final review because
the alleged normal theme-lease state was synthetic. The original recovery gate
is retained, with a regression verifying that mixed evidence is preserved.

The DoloWOLF launcher was refreshed from tracked source after the 0.2.0 live
pass and again for the final Start/Stop failure paths. Its read-only doctor
verifies the installed controller code 3, theme
client code 1, admitted ColorBlendr code 42001, all expected signers, ordinary
ADB and an absent managed runtime. Separate launcher actions now open either
ColorBlendr or the first-party Theme Client through the typed system-theme
broker.

The hardened Theme Client build was subsequently installed and revalidated on
PiXi. Its selected palette/style now survive theme-driven Activity recreation,
and natural expiry displays specific reconciliation guidance without treating
elapsed local time as cleanup proof. Exact restoration, reconciliation and
final zero-runtime shutdown passed again.

The paired build is byte-reproducible across consecutive clean signed host
builds; see `REPRODUCIBLE_BUILD_2026-09-28.md`. The exact deterministic
controller, theme-client and broker artifacts passed their supervised PiXi
grant/revoke/expiry/restoration gate. RC1 was staged, audited and promoted as a
private candidate; see `CANDIDATE_STAGE_2026-09-28.md` and
`RELEASE_PROMOTION_2026-09-28.md`. RC2 subsequently added the hardened helper
and updated operator guidance, was audited, and was promoted without changing
the PiXi-validated Android artifacts; see
`RELEASE_PROMOTION_RC2_2026-09-28.md`. Both are private candidates, not public
releases. The verified 0.1.0 baseline remains unchanged. A later DoloWOLF-only
desktop-shortcut improvement is in the active source and is not part of RC2.
