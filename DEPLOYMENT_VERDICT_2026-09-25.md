# Deployment verdict: proceed to a supervised external-client theme test

Reviewed source: `cf7a99a50e0aa5abf831fe07bf453a34c45f17f2`.
Scope: the paired first-party test client, controller, and SYSTEM_THEME_V1
broker on PiXi. This is permission to perform the bounded test below, not
production approval or approval of the ColorBlendr/Shizuku candidate.

## Review result

The blocking source findings in the two earlier deployment reviews have been
addressed sufficiently to proceed to this supervised test. The controller
invalidates older approvals using persisted generations, reserves submission
before dispatch, checks ownership/phase/generation/expiry before delivering a
grant, and reconciles saved state rather than constructing a grant from it.
The test client preserves uncertainty and accepts only operation-specific
confirmed-clean results. Both manifests request overlay hiding. Snapshot
failure preserves recovery evidence; local APK identity and signer checks
precede the helper's installs. No additional source blocker was identified
for this narrow test.

The existing successful 135-check build and GitHub verification were reused;
this review did not rebuild unchanged source or regenerate the signing key.

## Android evidence obtained during this review

PiXi reported tokay, Android 16, LineageOS 23.2, shell UID 2000 and SELinux
Enforcing. There was no managed runtime or NullGate broker process.

The installed controller APK was archived under
`device/logs/review-cf7a99a/controller-before.apk`. Its sole signer matched both
new APKs. Artifact checksums and local package/version identities were checked.
The controller was updated without clearing data and the paired test client
was installed. Both installed APKs were pulled back and byte-compared to the
reviewed artifacts.

These installs used ordinary non-root ADB as a separately reviewed UI-test
procedure. The existing privileged deployment helper was not invoked or
modified: its root requirement remains in place for its guarded workflow.

Observed through Android's UI hierarchy:

- The client recognized the paired controller and enabled Request.
- Request opened the protected approval Activity without a permission crash;
  it correctly identified NullGate Test Client, seed #876A4B, TONAL_SPOT and
  the 60-second duration.
- Deny returned DENIED_BY_USER and re-enabled Request.
- Terminating both apps with an outstanding approval and reopening the client
  preserved unresolved state and disabled Request.
- Reconciliation before any submission confirmed no record and restored Ready.
- Approval with no broker available produced the recovery-required state and
  disabled both approval controls.
- Terminating and reopening both apps after that failure again preserved
  unresolved state and disabled Request.

Local XML evidence is saved in `device/logs/review-cf7a99a/`. ADB remained
UID 2000, SELinux remained Enforcing, and the runtime remained absent at the
end. No privileged broker was started or theme operation performed.

## Current device handoff and exact next test

The test client is intentionally left showing an unresolved result from the
broker-absent test. Do not clear either app's data to dismiss it.

1. Have the operator enable Rooted debugging. Establish rooted ADB and run
   the existing serial-pinned platform/signer/runtime preflight.
2. Save the exact current theme value before starting SYSTEM_THEME_V1.
3. Start only that reviewed broker mode. Use the client's Check / reconcile
   action first; it must confirm cleanup and enable Request before proceeding.
4. Test one approved 60-second lease with immediate revoke, then one with
   expiry. Compare the restored theme to the saved baseline after each.
5. Use the guarded stop/cleanup path, archive the log, confirm zero leases and
   absent runtime, return ADB to non-root, and disable Rooted debugging.

Any unconfirmed restoration, unexpected identity, or uncertain broker state
stops this sequence and preserves recovery evidence.

## Limits of this verdict

The live checks establish launch, real caller recognition, denial, interrupted
approval, broker-absent handling and process-restart behavior. They do not
exercise every overlapping Activity interleaving or inject SharedPreferences
disk failures. Pure guard tests are not full Android lifecycle tests.

Userspace expiration cannot guarantee immediate restoration during suspend,
broker death or blocked platform calls. The supervised run therefore needs
the host recovery path and an independent baseline comparison. The process
runner confirms termination on its timeout path, but its exceptional finally
path does not assert successful termination after the second wait; it is not
proof of unconditional process termination. Production hardening remains.

Keep the current review model through the bounded root-backed validation.
After verified restoration and clean shutdown, routine documentation and UI
work can return to Sol Medium.

## Reviewed artifact hashes

```text
94629dd8c0e5efffeb926369ee162ec38c139e1807263c7a6388d5f64d49e520  NullGate-prototype-debug.apk
dfff0482829e2a72607b3105cdf03898b33e0901b897b07eebe7028396b7f0c9  NullGate-test-client-debug.apk
e2cf0b10b1b24109539d06f9f67f65eb10b3b7e649228bf3dc62f7f98648b243  NullGate-broker.jar
```
