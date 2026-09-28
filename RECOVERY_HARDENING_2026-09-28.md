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

A simulated archive-write failure and a copy-corruption regression confirm
that both failures are rejected and the runtime and theme receipt remain present. The existing
successful VALUE and NULL restoration scenarios continue to pass.

Validation: shell syntax, the full simulated v2 device-helper suite, launcher
tests and Git whitespace checks. All device interactions in these suites use
test doubles; no live phone operation or APK build was performed.

The installed DoloWOLF launcher matches the tracked launcher byte-for-byte and
calls `source/device/nullgate-device-v2.sh` directly. It therefore uses this
updated helper without reinstallation. This wiring was inspected without
contacting PiXi.

The preserved RC1 bundle does not include this follow-up change. Its existing
validation records remain applicable to its bytes. Live validation of this
follow-up remains pending.
