# NullGate 0.2.0 remediation review — 2026-09-28

Disposition: ready to enter the supervised PiXi deployment and live-test gate.
This records a source and host-verification review, not successful installation
or live restoration. The verified 0.1.0 release remains the release baseline.

Reviewed source: branch `pixi-private-colorblendr`, based on
`9dcf82aabbbb94e8b8fa763f8706cf9bb69d6ad8`. The remediation is preserved in
the next project checkpoint commit.

## Findings resolved

- Broker `NOT_FOUND` during reconciliation preserves the controller recovery
  record and requires host recovery. Only a confirmed `REVOKED` receipt permits
  the cleanup-success path. Controller-local reconciliation with no durable
  record is a separate existing response and is not proof of broker restoration.
- Launcher Stop checks for an orphan broker even when the runtime is absent.
  Stop and Recovery verify ordinary-shell UID after unroot before reporting
  success. Root/unroot reconnect waits are bounded to 15 seconds.
- Controller installation refuses an existing runtime or broker. Theme-client
  installation requires its own acknowledgement, explicit serial, local package
  and version, matching signer, installed controller code 3, and absent runtime
  and broker. Both new installation and update paths verify the installed signer.
- Desktop activation supplies a terminal for the menu fallback without Zenity.

## Review follow-up and evidence

The simulated device now records test-client and theme-client installation
independently, including coexistence and a theme-client update. Negative cases
cover wrong package/version/signer, old or missing controller, non-root ADB,
unavailable process inventory, existing runtime, a broker outside that runtime,
installation failure and mismatched acknowledgement tokens.

Six repeatable launcher tests cover unroot failure, reconnect timeout, ADB
remaining root, unavailable identity, an orphan broker, and verified success.
These tests replace every device-facing call; they do not contact PiXi.

The preceding signed host build passed controller/client/broker tests and APK
signature verification. This review verified the compiled controller calls the
new cleanup policy, rechecked candidate checksums and controller/theme-client
signatures, and passed the expanded simulated-device and launcher tests, shell
syntax, generated desktop validation, release metadata and diff whitespace checks.
The review follow-up changes shell scripts, tests and documentation only; the
Android binaries below are unchanged from that host build.

Candidate SHA-256 values:

```text
9819b64e6bc009d9dfce56b1342cfd31a9969dfcc74edd76821fcd958db333b6  NullGate-prototype-debug.apk
6b268e239e64f0c54ad965bfd5ffff74e7d7cb8bc6688a88515721ae7abb15d6  NullGate-test-client-debug.apk
8693168fe64c17ac6c4bb1ec30877f77c9f4b96eeeff5eebd42f52e963f8e1a0  NullGate-theme-client-debug.apk
74f904b083632a6b46fa50f0f749c7b9debf743aa4e42615f0f4e2974998ef32  NullGate-broker.jar
```

Controller and theme-client sole signer SHA-256:
`fc122f22e4716ba03cae81893783e437553d4a3ad21e897f4efe3367c415bafe`.

## Remaining live evidence

Follow `DEPLOYMENT_GATE.md`: archive the installed controller before update,
verify device/platform and absence of runtime/broker during the supervised root
window, and verify pulled-back APK hashes against this candidate. Test visible
user approval, immediate revoke and the theme client's 120-second expiry, each
with independent exact theme-restoration checks. Stop and clean through the
helper, verify ADB UID 2000 and disable Rooted debugging afterward.

No APK installation, broker launch or live lease test occurred during this
review. PiXi's ADB identity was observed as UID 2000, `u:r:shell:s0`. The phone's
Rooted debugging toggle was not inspected or changed. The installed DoloWOLF
launcher was not refreshed; the fixes currently reside in the source tree.

## Live-gate follow-up

The reviewed controller and theme client were subsequently installed and
pulled back byte-for-byte. Before the first new lease, the approval screen found
a durable ColorBlendr `RECONCILING` record left by the completed 0.1.0 session
and correctly disabled approval for the different client. No new lease was
issued and the theme did not change. The fresh broker was stopped, its log was
archived, the runtime was removed, the exact pre-test theme hash was confirmed,
and ADB returned to UID 2000.

The added `recover-controller-record` gate addresses this real upgrade state
without weakening broker `NOT_FOUND` handling. Its simulated tests cover exact
archival/removal and refusal on mismatched theme, unsafe record, unexpired
deadline, existing runtime, live broker and wrong mutation acknowledgement.
