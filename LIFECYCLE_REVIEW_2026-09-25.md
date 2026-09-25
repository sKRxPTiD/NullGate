# Lifecycle review: proceed to the bounded PiXi rerun

Reviewed source: `6776679`, including the retained-operation implementation in
`4769345`. This permits the supervised first-party SYSTEM_THEME_V1 test only.
It does not establish that the device failure is fixed until that rerun passes.

## Findings and follow-up

The operation object survives configuration recreation, but is absent after
process death. Reattachment revalidates the result-bound caller and request,
preserves the original generation, and does not issue another broker request.
Final grant delivery still checks record ownership, phase, generation and
expiry. A revoked, replaced or expired grant therefore takes reconciliation.
Explicit revoke and recovery operations also retain their transport result.

Two review fixes were applied in 6776679: event delivery now waits for onResume
and stops during onPause; recovery-required state survives recreation. This
avoids synchronous result consumption during onCreate followed by overwriting
the result status, and avoids presenting an already failed operation as pending.

Verification: the signed build completed with 153 host checks, both APK
signatures verified, packaged identity/permissions checked, and device-helper
simulations passed. The helper tests do not execute Android Activity retention.
Read-only PiXi checks found UID 2000, SELinux Enforcing and no managed runtime.
No device installation, root activation or theme change occurred in this review.

## Remaining live gate

After the operator enables Rooted debugging, use the serial-pinned helper and
existing signer/archive safeguards to install this controller. Preserve the
current theme baseline, start SYSTEM_THEME_V1, and reconcile any old request.
Require an actual GRANTED receipt across theme-triggered recreation, followed
by exact restoration for immediate revoke and natural expiry. Verify clean
shutdown, archive logs, remove the validated runtime and return ADB to non-root.

Stop and preserve evidence on any unexpected result. Process-local retention
does not remove the existing process-death, suspend or platform-call limits.
Keep Astra through this test; return to Sol Medium after verified cleanup.
