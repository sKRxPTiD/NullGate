# Theme-recreation lifecycle repair

This repair addresses the safely recovered device failure recorded in
`DEVICE_TEST_2026-09-25.md`. It has not yet passed the resumed privileged PiXi
test.

## Changed behavior

The controller now retains a process-local operation object across Android
configuration recreation. That object binds the operation kind, validated
caller/request fingerprint, approval generation, single-dispatch state and
event delivery. The recreated Activity revalidates the real caller and payload,
then attaches to the existing operation without reserving a new generation or
submitting a second broker request.

The transport result is published to whichever Activity instance is current.
Request issue, explicit revoke and reconciliation all use the same handoff, so
theme application and restoration can recreate their screens without losing
the broker response. Issue transport failure advances once to fail-closed
reconciliation; cleanup transport failure stops for host recovery instead of
retrying indefinitely.

The retained object is not durable. After actual process death it is absent,
so the controller still advances the durable generation and reconciles any
record rather than reconstructing a grant from preferences.

## Verification

The signed build passes 151 host checks plus the stateful device-helper suite.
The new checks cover single dispatch, identity matching, listener replacement,
a result arriving before the recreated UI attaches, one-time result claim,
continuation into cleanup and distinct issue/cleanup transport failures.

The resumed PiXi gate must reproduce the configuration recreation observed in
the failed run, then pass immediate revoke, natural expiry, exact restoration,
zero-lease shutdown and runtime removal. Astra review remains required before
that privileged run.
