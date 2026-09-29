# NullGate status — 2026-09-29

## Active source and PiXi install: NullGate 0.3.0 root switch

The project directive is choose app → root ON → open app → make changes →
close app → root OFF. Applied changes persist. Prior temporary-theme results
below do not satisfy that directive and are retained as legacy test history.

The default source build and installed `org.nullprotocol.nullgate` now use the
non-debuggable root-switch screen (version code 4). The signed 0.3.0 upgrade
was installed over 0.2.0 without clearing app data. Its root switch opened
ColorBlendr's real root service; OFF killed that service, and the applied
system color remained. The paired debug candidate also passed wrong-UID denial,
abrupt broker-loss cleanup and guarded receipt recovery. The existing artwork
is unchanged.

The default signed production build is in `dist/`. A separate private 0.3.0
release is packaged from a committed source snapshot under ignored
`dist/release-candidate/0.3.0-<source-commit>/`. The earlier operations-only
staging remains under `dist/release-candidate/0.3.0/` as historical evidence.
The private integration patch is saved in
`integrations/patches/v3-0003-Add-NullGate-manual-root-bridge.patch`.
The immutable 0.1 and 0.2 release bundles are preserved.

The **NullGate · PiXi Root Switch** desktop shortcut is the active host
launcher. The existing **NullGate · PiXi** shortcut remains the historical
theme workflow. PiXi has no app-callable `su`, so host bootstrap remains
necessary after reboot;
generic APK compatibility requires client integration rather than more lease
adapters. The upstream maintainer's response is not implied by private testing.
Keep rooted ADB on during a session: a tested non-root adbd restart ended the
broker, correctly producing unavailable/unknown status. Clean Stop must precede
disabling rooted debugging; a host-independent daemon lifetime remains unimplemented.

The ColorBlendr and production-controller integration is verified through the
complete phone switch flow. The final 0.3.0 private release contains signed
runtime artifacts, operator records and the exact committed source archive.
It is additive to the historical bundles. Signing secrets are excluded.

See `root-client/VALIDATION.md` and `root-client/USAGE.md` for the current evidence
and simple operating directions.

## Legacy state as recorded on 2026-09-28

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
The updated client and acceptance evidence are preserved in the private RC3
bundle under `releases/`; RC2 remains unchanged.
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
