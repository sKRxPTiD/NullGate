# External-client deployment review: HOLD

Reviewed source: `d461ec9d3d674ba0e1543a0250bef54a9f07acd3`.
This review supersedes earlier readiness claims. Passing host checks and CI
does not establish readiness for this privileged device test.

Only read-only device checks were performed. PiXi was connected; ADB reported
UID 2000 and SELinux Enforcing. No APK installation, root activation, broker
launch, or theme write was performed during this review.

## Confirmed blockers and required regression tests

1. **Failed snapshot can cause destructive rollback.** In
   `SystemThemeSeedAdapter.activate`, `ownedLeaseId` is assigned before
   `backend.snapshot()` succeeds. If snapshot throws, BrokerEngine calls
   deactivate, which restores the uninitialized `prior` (null). For the Android
   backend this deletes the theme setting even though activation never had a
   valid baseline. Track snapshot acquisition and mutation explicitly. Never
   restore without a valid baseline. Test snapshot failure and assert zero
   restore/write calls and an unchanged prior setting.

2. **Controller/client wire contract mismatch.** Controller returns
   `expiresAtElapsedMillis`; test client and its response-policy tests expect
   `expiresElapsed`. Every real grant is rejected by the harness. Use one shared
   contract and a cross-component test built from controller-produced keys.
   Also require all three receipt fields specifically for GRANTED; the current
   pure validator accepts a decision-only field set when other values are
   passed separately to requireFreshGrant.

3. **Uncertain grants become Ready.** Test-client MainActivity catches invalid
   grants or persistence failure, says Not granted, then refreshState replaces
   that with Ready when no receipt exists. There is no durable pending marker
   before dispatch. A controller lease may already be active. Persist pending
   state before launching, retain uncertainty across process death and malformed
   results, block new requests until reconciliation, and preserve error text.
   Test result loss, expired receipts, persistence failure and process restart.

4. **Theme recovery cannot complete under its own token.**
   recover_system_theme_runtime requires the theme token but calls
   cleanup_runtime, which requires the marker token. It can restore and remove
   the snapshot, then fail at cleanup. Pass the validated recovery authority
   explicitly to internal cleanup without broadening public token acceptance.
   The existing fake-device tests only exercise ordinary theme deploy/stop,
   not snapshot recovery. Add complete successful and refused recovery cases.

5. **Controller request lifecycle is not serialized end to end.** Only broker
   transport is serialized. Multiple approval Activities can read/write the
   single preference record while queued responses update it without checking
   lease identity. Delayed recovery reads ACTIVE and returns the captured old
   ID using the current record's expiration without rechecking ownership or
   expiry. Guard each state transition with lease ID and caller identity;
   serialize reservation, settlement and reconciliation. Test overlapping
   approvals, rotation, delayed replies and replacement of the recovery record.

6. **Unverified snapshot truncation.** AndroidSystemThemeBackend allows 16 KiB
   snapshots but silently returns at most 4096 bytes from readBounded. Exact
   restoration cannot be claimed from a truncated baseline. Detect overflow
   and fail before recording or mutation; drain process output safely within
   the command deadline. Test oversized output, timeout and valid maximum size.

## Additional checks before reopening the device gate

- Revalidate caller identity/version at approval time, after the user may have
  left the confirmation screen open; exercise actual Android package visibility
  between controller and test client (neither manifest declares queries).
- Verify Activity recreation and persistence failures with Android lifecycle
  tests, not solely pure policy tests. Green CI currently compiles the Android
  paths but does not execute them.
- Verify local APK identity and signer before install, not just afterward.
- Rooted ADB must be established and checked during the eventual supervised
  test; this review deliberately did not enable it.
- Hard expiry uses a userspace polling thread. Suspend, process death and
  blocked Android calls can delay restoration. Do not describe it as an
  unconditional wall-clock privilege or restoration guarantee.

## Next work and usage checkpoint

Repair these bounded issues and add the specified regressions off-device.
Routine implementation can return to Sol Medium to conserve allowance.
Then use the deployment reviewer again on the repaired diff before any PiXi
write. Keep the original signing identity and installed data intact.
