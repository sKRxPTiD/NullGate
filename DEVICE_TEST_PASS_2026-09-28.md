# NullGate 0.2.0 PiXi device-test pass — 2026-09-28

Disposition: the supervised 0.2.0 controller and first-party theme-client gate
passed on PiXi. The immutable 0.1.0 release bundle remains the release baseline;
this test does not promote or replace that bundle.

## Device and installed artifacts

- Device: Google Pixel 9 (`tokay`), serial `54110DLAQ0043W`
- Android 16, LineageOS 23.2, SELinux Enforcing
- Controller: 0.2.0, version code 3
- Theme client: 0.1, version code 1
- Controller APK SHA-256:
  `9819b64e6bc009d9dfce56b1342cfd31a9969dfcc74edd76821fcd958db333b6`
- Theme-client APK SHA-256:
  `8693168fe64c17ac6c4bb1ec30877f77c9f4b96eeeff5eebd42f52e963f8e1a0`
- Both installed APKs were pulled back and compared byte-for-byte with the
  reviewed host candidates. Their sole signer matched the pinned digest
  `fc122f22e4716ba03cae81893783e437553d4a3ad21e897f4efe3367c415bafe`.

The installed 0.1.0 controller was archived before update. The pre-test theme
was recorded exactly, with SHA-256
`4de9a2f6befef3db0768795e86eb4183f5dbbb97c176c5c7a4c6514e2e197580`.

## Upgrade-state recovery

The first approval attempt correctly refused to cross an unresolved ColorBlendr
record left by the earlier 0.1.0 session. No new lease was issued and the theme
did not change. The dedicated controller-record recovery archived and removed
only the exact hash-locked, expired record after verifying the device, signer,
controller version, client identity, lease ID, restored-theme hash, ownership,
file mode, expiry, and absence of a broker runtime.

TerraDrive's FUSE mount does not preserve a requested archive mode. The helper
therefore copies the record, attempts the restrictive mode, and independently
verifies the archive hash before deleting the device record. Recovery then
completed without weakening the controller's conservative `NOT_FOUND` policy.

## Live lease results

The first-party theme client passed visible protected approval. A temporary
`#6750A4` `VIBRANT` lease was granted, changing the secure theme record hash to
`338f66e94202c3fdf2b586a66cdfcc31feccdfb8d1376a5dffeb5d43a02c5422`.
Explicit revoke produced the broker's `REVOKED` cleanup result and restored the
original theme exactly.

The original broker then reached its independent 15-minute hard deadline while
a second approval was open. Approval was disabled, no theme mutation occurred,
and the controller retained a conservative reconciliation record. After its
bounded lease window elapsed, the guarded recovery path archived and removed
that exact record and a fresh broker was started.

An `#9B3D20` `EXPRESSIVE` lease was then granted. Its active theme hash was
`5d8e6c6c4e8cfd6ef7505c169dad09451aa417740d4da8faa245582107115d69`.
No revoke was requested. At the 120-second deadline the broker recorded
`cleanup=EXPIRED` and restored the original theme hash exactly. Client
reconciliation reported: `Reconciled clean: no active theme lease remains.`

## Final state

- Original secure theme restored exactly.
- No active or unresolved lease remains; the controller preference contains
  only approval generation `3`.
- Broker reported zero leases before shutdown.
- Broker log was archived and the managed runtime was removed.
- The managed runtime path passed `verify-clean`.
- ADB was returned to UID 2000 (`u:r:shell:s0`) with SELinux Enforcing.
- Rooted debugging was switched off by the operator after ADB returned to UID
  2000. SELinux remained Enforcing and no managed broker runtime remained.

## Hardened-client follow-up

The subsequent lifecycle-UX fix was installed and revalidated in a separate
supervised window. The prior installed theme-client APK was archived with
SHA-256
`8693168fe64c17ac6c4bb1ec30877f77c9f4b96eeeff5eebd42f52e963f8e1a0`.
The replacement was pulled back byte-for-byte with SHA-256
`20c55342a64774c96da6bddd9af80ecd0cc5f3ce316861c12e0a2419cc9f5db2`
and retained the pinned first-party signer.

An Ember `#9B3D20` / `EXPRESSIVE` lease confirmed that Android theme-driven
Activity recreation preserves the submitted palette and style on screen. At
natural expiry the broker recorded `cleanup=EXPIRED`, restored the exact
baseline hash, and the recreated client retained Ember/EXPRESSIVE while showing
`Lease deadline passed; reconcile to confirm restoration.` Reconciliation then
reported no active lease. Final shutdown confirmed no controller record, zero
leases, an absent managed runtime, ADB UID 2000 and SELinux Enforcing.
The operator then switched Rooted debugging off, and the installed DoloWOLF
doctor passed all package identity, signer, version and clean-idle checks.
