# NullGate Test Client

The NullGate Test Client is a first-party external Android app used to validate
the real app-to-controller boundary without modifying or impersonating
ColorBlendr. Its package is `org.nullprotocol.nullgate.testclient`, version code
1. It runs under its own Android UID.

## What it can do

- Verify that the installed NullGate controller has the same sole current
  signing identity as the test client.
- Open the explicit, result-bound NullGate approval Activity.
- Request exactly one opaque theme seed, the `TONAL_SPOT` style, and a fixed
  60-second duration.
- Receive and persist only the returned lease ID and elapsed-time expiration.
- Request immediate revocation of that recorded lease.

Controller responses are accepted only when their field set is exact. A grant
must contain a bounded lease identifier and an expiration that is still in the
future but no more than 60 seconds away. Revocation is confirmed only by an
exact decision-only result carrying `REVOKED`.

It receives no root identity, shell, file descriptor, Binder service, arbitrary
command channel, or direct broker connection.

## Controller admission

The controller admits the test package only when all of these are true:

- protocol version 1;
- result-bound Activity caller;
- owner-user ordinary app UID;
- exact package and version code;
- sole ownership of the caller UID;
- exactly one current signer; and
- that signer exactly matches the controller's own current signer.

This is deliberately different from the ColorBlendr policy, which pins
ColorBlendr's independent upstream signing certificate and reviewed version.
The test-client rule proves the external-app lifecycle without weakening or
pretending to satisfy the third-party signer boundary.

## Build boundary

`./build.sh` produces `dist/NullGate-test-client-debug.apk` and signs it with the
same local development identity as the controller. Both APKs are included in
`dist/SHA256SUMS`. Git excludes the signing identity and all generated APKs.

These artifacts are prototypes. Do not publish the local key, substitute the
test client for a third-party integration review, or treat a successful harness
run as a production approval.

No PiXi deployment is authorized merely because this APK builds. Device
installation and broker activation require the separate privileged deployment
review and guarded operator procedure.
