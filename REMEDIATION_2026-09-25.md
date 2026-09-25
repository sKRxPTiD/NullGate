# External-client remediation status

Baseline review: `DEPLOYMENT_REVIEW_2026-09-25.md` at `fa32bd0`.
This file records implementation work after that HOLD. It does not reopen the
PiXi write gate; a fresh security review is still required.

## Implemented off-device

1. Theme activation tracks baseline acquisition, snapshot-write attempts and
   possible mutation independently. A failed baseline read performs no restore.
2. Controller and clients compile against one exact wire contract, including
   `expiresAtElapsedMillis` and a typed reconciliation action.
3. The test client durably records `PENDING` before dispatch, retains
   `PENDING`/`UNKNOWN` across restart and malformed or lost results, blocks new
   requests, and exposes explicit fail-closed reconciliation.
4. Theme snapshot recovery passes its already validated narrow theme authority
   into internal cleanup. Fake-device tests cover success, wrong-token refusal
   and unsafe-receipt refusal.
5. Controller preference transitions are process-serialized and conditional on
   lease ID, client package and UID. Approval revalidates the caller and reviewed
   version immediately before reservation. Delayed grants require the same
   record, ACTIVE phase and an unexpired elapsed-time window. `singleTop` was
   removed so overlapping requests are independently revalidated.
6. Fixed-command output is drained concurrently, rejected on overflow rather
   than truncated, and bounded by a tested timeout. A maximum-size read and
   overflow behavior are covered by host tests.
7. The device helper verifies each local APK signer against the controller pin
   before invoking ADB installation. Package-visibility declarations now cover
   the paired controller/test client and reviewed ColorBlendr package.
8. UI and documentation no longer claim unconditional wall-clock restoration;
   they state the watchdog/suspend limitation.

## Verification completed

- 114 host Java checks pass.
- Signed controller and test-client APK builds pass with one signer each.
- Stateful fake-device recovery and install gates pass.
- No PiXi write was performed during remediation.

## Deliberately still gated

- Android Activity recreation, task/result delivery and package visibility need
  a supervised on-device test; host tests cannot prove framework behavior.
- The repaired diff needs a fresh Astra security/deployment review.
- Only after that review changes HOLD to GO may the narrow PiXi test begin.
