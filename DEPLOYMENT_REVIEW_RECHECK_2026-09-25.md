# Re-review decision: HOLD

Reviewed commit: dd07d46325977377ae0e5e81320d0e92e797acdb.
This is a source-level deployment review. No device commands were issued.
The implementation summary in REMEDIATION_2026-09-25.md is not deployment approval.

## Blocking findings

### P1: Missing permission for both protected Android entry points

ClientRequestActivity.java:38 and test-client MainActivity.java:54 call
setHideOverlayWindows(true) on API 31+, but neither manifest requests
android.permission.HIDE_OVERLAY_WINDOWS. These calls occur outside their
request-validation exception handling. The approval and test-client entry
points therefore do not satisfy the platform API's permission requirement.
The controller's ordinary launcher working does not test this separate path.

Add the normal permission to both manifests. Verify the packaged manifests
and exercise both Activities on Android before permitting a privileged request.
Official requirement:
https://developer.android.com/security/fraud-prevention/activities

### P1: An uncertain grant still becomes CLEAN through a denial-shaped reply

ClientRequestActivity.java:274-280 can receive GRANTED, fail to persist its
settlement, and return DENIED_STALE_CONTROLLER_RESULT without revoking.
Test-client MainActivity.java:187 clears its durable pending record for every
non-GRANTED decision. TestClientResponsePolicy.validateDecision accepts any
nonempty decision string. Consequently this actual post-grant failure path
enables another request while the earlier root-side effect may remain active.
CLEANUP_FAILED and unknown future decision strings are also classified clean
by the same client branch.

Define exact, operation-specific response outcomes with separate confirmed
denial, confirmed cleanup, and uncertain categories. Settlement failure after
dispatch must retain UNKNOWN and reconcile. Do not classify by "not GRANTED".
Test broker grant followed by failed controller persistence, unknown response
codes, inconsistent result status/schema, and failed client persistence through
the real result-handling path.

### P1: Grants and reconciliation still use stale controller state

ClientRequestActivity.java:230-238 returns GRANTED from persisted ACTIVE without
consulting the broker. ElapsedRealtime resets on reboot, so a previous-boot
expiration can look future again even though the ephemeral broker is gone.
The direct grant callback at 283-284 also does not recheck current phase or
expiration immediately before delivery.

Revoke/reconcile does not reserve a REVOKING phase before queuing transport.
A recreated approval Activity can therefore read ACTIVE while cleanup is
already in progress and return a grant for that lease. In addition,
handleReconcile returns NOT_FOUND when no lease record exists even if an older
approval screen is still awaiting consent; that screen can later reserve and
issue after the client was told reconciliation was clean.

Persist a request generation before approval, serialize approval reservation,
revocation reservation and completion, and invalidate prior pending approvals
on reconciliation. Recovery must revoke or query authenticated live broker
state; preferences alone must never manufacture GRANTED. Bind any persisted
elapsed timestamps to a boot/session identity. Recheck ownership, phase,
generation and expiry immediately before result delivery.

Tests must execute these interleavings: outstanding approval -> reconcile ->
old approval; ACTIVE -> queued revoke -> recreated approval; broker reply ->
delayed UI callback -> expiry; and persisted ACTIVE across a new boot.
The eight new pure comparison checks do not exercise these workflows.

## Additional hardening and evidence gaps

- SystemThemeSeedAdapter sets snapshotWriteAttempted before exclusive receipt
  creation. If creation fails because a receipt already exists, cleanup can
  delete that preexisting receipt without owning it. Broker startup rejects
  a stale receipt, reducing the ordinary-path exposure, but cleanup ownership
  still needs to distinguish failed open from an owned partial write.
- BoundedProcessRunner ignores whether the post-kill wait actually confirms
  process exit and lacks finally-based cleanup on interruption/reader failure.
  Test child exit confirmation and resource closure, not just a timeout exception.
- Installation now checks signer before installing, but not APK package
  identity/version. A same-key APK with a different package would pass this
  check. Validate both local APK identities before installation.
- Recovery tests added VALUE success, wrong token and symlink refusal. Add NULL,
  failed restoration/readback, and receipt preservation checks.
- Android Activity lifecycle and SharedPreferences persistence failures have
  not been executed by the host suites. Add framework-level tests with fake
  transport where possible; compilation and pure helpers do not establish
  lifecycle correctness.

## Confirmed improvements

The shared expiration key is consistent, failed baseline reads no longer
restore null, theme cleanup receives the correct token, and bounded output
reading rejects overflow instead of silently truncating. Package visibility
and pre-install signer checks are present. These improvements do not resolve
the blocking result/state transitions above.

## Next checkpoint

Sol Medium can implement the concrete repairs and regression coverage.
Keep PiXi privileged testing on HOLD. Return to the deployment reviewer once
the actual Activity/result paths and overlapping requests have been exercised.
