# First-party external-client theme test: PASS

Tested implementation: 6776679, reviewed in LIFECYCLE_REVIEW_2026-09-25.md.
This result supersedes the lifecycle blocker in DEVICE_TEST_2026-09-25.md for
the bounded first-party SYSTEM_THEME_V1 workflow on PiXi.

## Observed evidence

The operator enabled Rooted debugging. The guarded preflight verified tokay,
Android 16, LineageOS 23.2, UID 0, u:r:su:s0, SELinux Enforcing, the installed
controller signer, and absent runtime. The old controller APK and original
theme setting were saved locally before updating the paired APKs. Both guarded
installers verified the package/version and signing identities. The broker
artifact was hash-checked during deployment.

1. The first-party client opened the result-bound approval Activity. Approval
   produced a real GRANTED receipt in the client. Android event logs recorded
   configuration relaunches of ClientRequestActivity with mask 80000000, so
   the repaired path encountered the same trigger as the previous failure.
2. Immediate revoke returned REVOKED to the client. The broker recorded cleanup
   and the theme matched the pre-test baseline byte for byte.
3. A second approved request returned GRANTED. The active setting was verified
   to contain palette 876a4b and style TONAL_SPOT. No revoke was sent during its
   lease window. The broker recorded cleanup=EXPIRED, and the theme again
   matched the baseline byte for byte.
4. The client was restarted after expiry. It required reconciliation instead
   of assuming cleanup; reconciliation confirmed clean state and enabled a new
   request.
5. Guarded stop confirmed broker exit and zero lease artifacts. Cleanup
   archived the broker log and removed the managed runtime. The final theme
   still matched the baseline. ADB was returned to UID 2000 with SELinux
   Enforcing and runtime absent.

The before/revoke/expiry/final setting captures all have SHA-256:
`5e10265399acbef8dc716eb05ced3d014f5d91e044238f2c6f823d4850c4f67f`.
Local APK archive, UI XML, lifecycle events and setting captures are under
`device/logs/review-6776679/`; the guarded helper archived the broker log under
`device/logs/`. These local artifacts are intentionally not published.

## Scope and handoff

The paired controller and test client remain installed. The operator must turn
the Rooted debugging setting off; returning adbd to non-root does not toggle it.
Routine operator-guide/UI work can return to Sol Medium now.

This passes the supervised external-client typed theme capability. It does not
prove universal app compatibility, production readiness, arbitrary root leases,
ColorBlendr integration or the optional Shizuku path. Suspend, process-death and
blocked-platform-call limits still apply as documented in the review.

## Tested artifacts

```text
60968c984a0e9ce49200609d5facc2422a8e91b1e4792785d1f867482970ffba  NullGate-prototype-debug.apk
dd3dbd442e5160b4f1ebb0d077d31899d617f32256ebaf81ee28fe708734aecb  NullGate-test-client-debug.apk
0ebb9b7f08b5bf9b87125d132c071206a8f8bea3718de71ca8ad7e0f6fe6ad5f  NullGate-broker.jar
```
