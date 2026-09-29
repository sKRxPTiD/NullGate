# NullGate PiXi private 0.2.0 RC3

This private candidate packages the PiXi-validated 0.2.0 controller with the
foreground Theme Client countdown fix and updated operator/recovery records.
It is additive; the 0.1.0 baseline, RC1 and RC2 remain unchanged. It is not a
public release.

## Included and verified

- Controller 0.2.0, version code 3; paired signer remains pinned.
- Theme Client 0.1, version code 1, updated to refresh its visible countdown
  while foregrounded and expose reconciliation at expiry without a restart.
- Typed system-theme broker and guarded DoloWOLF launcher/recovery helper.
- Updated operator guide and supervised interrupted-theme recovery record.
- Tracked source archive at the exact commit recorded in `BUILD_INFO.txt`.

On 2026-09-28/29, live PiXi acceptance verified temporary grant, interrupted
broker recovery to the exact saved theme, guarded controller-record recovery,
normal two-minute expiry while the client stayed open, reconciliation, guarded
Stop, ordinary ADB and absent runtime. Host checks and simulated helper and
launcher suites also passed. Evidence and limits are in
`LIVE_RECOVERY_REHEARSAL.md` and `STATUS.md`.

The private ColorBlendr fork remains a separate client. The Test Client is a
development harness and is omitted. No signing key or password file belongs in
this bundle.

RC3 is staged under ignored `dist/release-candidate/rc3/` for audit. Its
packager verifies clean committed tracked source, permits only the known
protected malformed untracked filename, checks reproducible artifact hashes
and the pinned signer, and does not install to PiXi or push Git. Promotion
adds a new ZIP and sidecar under `releases/` without overwriting earlier
release files.

Updated Theme Client SHA-256:
`b999f127f10caf934a0be2f8943e62c0faa81d2464f06c7a45c02e6f5e4c91d9`.
Controller and broker hashes are unchanged from RC2's live-validated set.
