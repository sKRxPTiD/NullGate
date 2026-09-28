# Theme recovery archive verification — 2026-09-28

Theme recovery now verifies the SHA-256 of the host archive against the pulled
recovery receipt before removing the device receipt or cleaning the runtime.
An archive write failure or byte mismatch aborts with the device receipt
preserved. Exact theme restoration still precedes archival, so this failure
can leave a restored theme with a retained receipt; it does not prove runtime
cleanup and the operator must inspect or retry recovery.

Each theme archive is reserved with a unique filename so recoveries within the
same second cannot overwrite earlier evidence. Reservation failure preserves
the device receipt. A repeated-recovery test verifies both archives survive.

Broker logs likewise use unique archive names. Cleanup now obtains a valid
device SHA-256 and verifies the downloaded log against it before deleting the
runtime. A simulated corrupt transfer confirms cleanup aborts with the runtime
unchanged. Transfer or hash failures require inspection or a retry.

Theme receipt downloads are also checked against a valid device SHA-256 before
parsing or restoring the theme. A corrupted transfer containing valid JSON is
rejected before restoration; the simulated device state remains unchanged.

Immediately before removing the theme receipt, recovery checks again for a
broker and rechecks the receipt hash. A simulated receipt change during the
operation preserves the receipt and runtime. These checks narrow the race
window; they are not an atomic lock against another privileged process.

Additional regressions cover empty or failed device hash commands for both
theme receipts and broker logs, and a broker appearing during theme restoration.
Unavailable hashes leave simulated device state unchanged. A newly live broker
blocks receipt removal and leaves runtime evidence present.

Process inventory now has a 15-second timeout with a 2-second termination
grace period. A simulated stalled ADB inventory reports UNKNOWN within the
bound and leaves runtime state unchanged. Other ADB operations retain their
existing behavior; this is not a deadline for the entire recovery operation.

A simulated archive-write failure and a copy-corruption regression confirm
that both failures are rejected and the runtime and theme receipt remain
present. The existing successful VALUE and NULL restoration scenarios continue
to pass.

The archive-copy test helper is recorded executable in Git, independently of
TerraDrive's uniform permission presentation, so fresh clones can run it.

Validation: shell syntax, the full simulated v2 device-helper suite, launcher
tests and Git whitespace checks. All device interactions in these suites use
test doubles; no live phone operation or APK build was performed.

The installed DoloWOLF launcher matches the tracked launcher byte-for-byte and
calls `source/device/nullgate-device-v2.sh` directly. It therefore uses this
updated helper without reinstallation. This wiring was inspected without
contacting PiXi.

The preserved RC1 bundle does not include this follow-up change. Its existing
validation records remain applicable to its bytes. Live validation of this
theme-receipt recovery follow-up remains pending.

## Supervised broker-cleanup check

On 2026-09-28, after Wolf enabled Rooted debugging, PiXi passed the helper's
platform and signer preflight. A marker-mode broker started as verified root
PID 20584 and stopped through the guarded helper without issuing a lease.
The hardened cleanup verified and archived the broker log, removed the runtime,
and passed `verify-clean`.

The secure theme value's SHA-256, including its terminal newline, was identical
before and after:
`5e10265399acbef8dc716eb05ced3d014f5d91e044238f2c6f823d4850c4f67f`.
ADB returned to the ordinary shell, and the installed launcher's read-only
doctor passed all app identities and the absent-runtime check. No APK was
installed and no theme lease was requested. The user must switch the Rooted
debugging toggle off separately.

This validates live broker-log hashing and cleanup. Theme snapshot transfer,
archival and concurrent-change failure paths remain covered by simulation;
their live recovery gate remains pending.
