# NullGate post-review remediation

This checkpoint addresses the deployment blockers recorded in
`DEPLOYMENT_REVIEW_RECHECK_2026-09-25.md`. It is an implementation and test
record, not permission to deploy privileged code to PiXi.

## Closed in code

- Both Android packages declare `HIDE_OVERLAY_WINDOWS`; the signed build checks
  that the controller APK contains it.
- External approvals are bound to a persisted generation. Reconciliation,
  revocation, replacement, expiry, and an existing record prevent an older
  approval from submitting or delivering a grant.
- A controller restart never manufactures `GRANTED` from preferences. Recovery
  takes the reconciliation path instead.
- The test client clears unresolved state only for an exact, operation-specific
  confirmed-clean result. Unknown, malformed, cleanup-failed, and future result
  codes remain `UNKNOWN`. Reconciliation cannot resurrect a grant.
- Snapshot ownership distinguishes an attempted write from an owned receipt;
  partial failures preserve recovery evidence and keep the adapter unavailable.
- Bounded subprocess cleanup confirms termination and closes all streams.
- The device helper validates local APK package name and version before ADB
  installation, in addition to signer and target-device checks.

## Verification at this checkpoint

- 135 host Java checks cover broker/security, external-client policy/state, and
  test-client response/lifecycle behavior.
- The stateful device-helper scenario suite includes wrong package/version,
  null-theme recovery, failed write/readback, and receipt-preservation cases.
- The signed build verifies both APK signatures, one signer per APK, controller
  package/version identity, and the overlay-hiding permission.

## Deliberately still required

- A fresh independent security/deployment review of this new checkpoint.
- Android framework lifecycle evidence on a non-privileged test run before the
  root-backed PiXi deployment gate.
- Rooted debugging remains off until that final deployment gate explicitly
  calls for it.
