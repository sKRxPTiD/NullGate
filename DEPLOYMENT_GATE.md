# PiXi external-client deployment gate

This checklist begins only after the off-device build and review are complete.
It does not authorize a device write by itself.

## Required before installation

- Use the reviewed Astra deployment pass.
- Connect exactly one intended PiXi device and pin its ADB serial explicitly.
- Enable Rooted debugging only for the supervised test window.
- Confirm Pixel 9/tokay, Android 16, LineageOS 23.2, UID 0 rooted ADB,
  `u:r:su:s0`, and SELinux Enforcing.
- Confirm the managed broker runtime is absent and no broker process exists.
- Pull and archive the currently installed controller APK before updating it.
- Verify that the new controller and test-client APKs share the installed
  controller's existing signer and that all hashes match `dist/SHA256SUMS`.
- Record the exact pre-test secure theme value for independent restoration
  comparison.

## Guarded sequence

1. Update the existing NullGate controller with the matching-signature build.
2. Install the test client using the dedicated `NULLGATE_TEST_CLIENT_V1` gate.
   The marker-test token cannot authorize this action.
3. Verify both installed APKs by pulling them back from PiXi and checking their
   sole current signer.
4. Start only the typed `SYSTEM_THEME_V1` broker mode.
5. Open the NullGate Test Client and request its fixed 60-second lease.
6. Confirm that NullGate identifies the requester as **NullGate Test Client**,
   shows the fixed seed, style and hard expiration, and requires a visible tap.
7. Exercise immediate revoke first. Confirm exact theme restoration, a clean
   lease inventory, and an intact controller recovery record.
8. Repeat once using hard expiry instead of immediate revoke and verify the
   same exact restoration.
9. Stop the broker through the guarded helper, archive its log, verify zero
   leases, remove only the validated managed runtime, and confirm it is absent.
10. Return ADB to non-root and disable Rooted debugging on PiXi.

## Stop conditions

Stop without improvising if any signer, package owner, version, UID, SELinux
context, hash, response schema, theme snapshot, process identity, lease state,
or cleanup result is missing or different from the expected value. `UNKNOWN`
is not success. Preserve the runtime and logs for inspection; do not manually
delete `/data/local/tmp/nullgate`.

## Rollback boundary

The test client can be removed after the broker is stopped and the runtime is
verified clean. A controller rollback must use the archived, same-signer APK
and must not clear controller data until any recorded lease or uncertain result
has been reconciled.
