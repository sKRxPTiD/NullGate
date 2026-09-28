# NullGate continuation checkpoint — 2026-09-28

## Working state

- Active source: `/mnt/TerraDrive/Null Protocol/Apps/NullGate/source`
- Branch: `pixi-private-colorblendr`
- Last implementation commit: `1508fe3dc8a8154e7e2c8f7c8e44cb78e0aaab81`
- Implementation and review checkpoints are pushed to the matching GitHub branch.
- Protected untracked filename remains untouched:
  `ha256sum -c NullGate-PiXi-private-2026-09-26.zip.sha256`

## Completed since RC1

Recovery verifies downloaded theme receipts before restoration, verifies host
receipt archives before deleting device evidence, reserves unique theme and
broker-log archives, and verifies broker-log transfers before runtime cleanup.
It rechecks broker absence before restoration and receipt deletion, rechecks
receipt identity before deletion, and bounds process inventory to 15 seconds
with a 2-second termination grace period.

The full simulated helper suite and eleven launcher regressions pass. Tests
cover corrupted transfers, archive write/corruption failures, empty or failed
hash commands, changed receipts, broker appearance and stalled inventory.
Launcher setup validation passes. The installed launcher calls the active
source helper directly. It was later refreshed for the failure-path unroot
fix, with the installed copy verified against source. Details and limits are in
`RECOVERY_HARDENING_2026-09-28.md`.

## Device and release state

A supervised no-lease broker start/stop passed live log hashing, archival,
cleanup, unchanged-theme verification and the final read-only doctor on PiXi.
ADB returned to ordinary shell. Wolf confirmed Rooted debugging is off.
No device operation is in progress.

Theme-receipt recovery itself still needs live validation. Do not count the
no-lease broker check as proof of that path. Do not manufacture a stale theme
receipt, kill a live broker, or clear controller records merely to force a test.
Plan the supervised gate before changing device state.

The preserved 0.2.0 RC1 ZIP and checksum still verify. Its helper predates this
hardening. The 0.1.0 baseline ZIP and sidecar retain their verified hashes.
Neither preserved release was updated by this pass.

## Safe continuation

Resume with status and this checkpoint; no context recovery from other chats
is required. Routine host work is interruptible. Signing files, preserved
releases, the protected filename and historical trees remain protected.
Use the lowest adequate model and notify Wolf before raising its level.

GitHub HTTPS authentication was subsequently made persistent on DoloWOLF.
The already-authenticated CLI 2.101.0 executable was copied byte-for-byte to
`/home/wolf/.local/lib/github-cli/2.101.0/bin/gh`. Global and GitHub-specific
credential-helper settings now use that path instead of temporary binaries.
Authenticated API access confirmed `sKRxPTiD`, and a push dry run passed without
obsolete-helper warnings. No new token or SSH key was created; credential
contents were not printed. Never print tokens or credential-file contents.
