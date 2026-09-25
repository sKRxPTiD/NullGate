# Proposed pull request

## Title

Add a typed NullGate temporary-theme work method

## Body

### Summary

This adds `NULLGATE` as an optional ColorBlendr work method. In this mode,
ColorBlendr requests a narrow, temporary system-theme lease from the separately
installed NullGate controller. ColorBlendr does not receive root, a shell, or a
privileged Binder service.

NullGate source, protocol contracts, threat model, security audit, and guarded
build instructions are published at
https://github.com/sKRxPTiD/NullGate.

### Request flow

- Verify the installed NullGate controller against its pinned signing
  certificate.
- Allow requests only while ColorBlendr is visible.
- Send only protocol version, opaque ARGB seed, supported Monet style, and a
  fixed 60-second duration through an explicit result-bound Activity request.
- Save the returned lease receipt, revoke an active lease before replacing it,
  and request immediate revocation when fabricated colors are removed.
- Fail closed for boot, Tasker, and other background-triggered changes because
  NullGate requires visible user approval.

NullGate independently verifies ColorBlendr's package name, sole UID ownership,
version code, and official signing certificate. It presents the confirmation
screen, applies the typed capability, schedules bounded expiry, and restores the
previous theme state.

### Deliberate limitations

- The first compatibility lease is fixed at 60 seconds.
- Background automation is not supported in NullGate mode.
- The `CMF` style is outside the initial capability contract.
- This patch does not add a generic command executor or change the existing
  Root, Shizuku, or Wireless ADB modes.
- The NullGate policy currently admits the reviewed ColorBlendr 3.0.1 signer
  and version code 42. Before a new official release can use this path, its new
  version code must be reviewed and admitted by NullGate.

### Verification

- `:app:compileDebugKotlin` passes.
- `:app:assembleDebug` passes.
- The exported patch applies cleanly to upstream commit
  `5b078e92abfa482674d82a23ce2a302d17cee756`.
- The integration introduces no new lint errors. The current upstream tree
  still reports its existing Compose `StateFlow.value` and missing Shizuku
  translation failures.

### Testing boundary

The local APK is debug-signed and deliberately cannot replace or impersonate
the official ColorBlendr package. Device testing requires an official
ColorBlendr-signed build and the matching reviewed NullGate version policy.

## Maintainer review points

- Is a foreground-only work method acceptable alongside the existing methods?
- Should the lease duration be configurable later, within NullGate's policy
  ceiling?
- Should background callers display a dedicated explanation instead of the
  current fail-closed error?
- Which release version code should NullGate admit after merge?
