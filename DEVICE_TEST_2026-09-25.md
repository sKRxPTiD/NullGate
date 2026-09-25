# External-client device test: safely stopped, lifecycle repair required

Tested source: cf7a99a50e0aa5abf831fe07bf453a34c45f17f2.
This result supersedes the conditional permission to proceed in
DEPLOYMENT_VERDICT_2026-09-25.md. The external-client happy path has NOT passed.

## Observed result

After the operator enabled Rooted debugging, the guarded helper verified
tokay, Android 16, LineageOS 23.2, UID 0, u:r:su:s0, SELinux Enforcing, the
controller signer and an absent runtime. The reviewed SYSTEM_THEME_V1 broker
launched. The client's prior uncertain request reconciled cleanly.

The first approved external request reached the broker and received GRANTED.
Android then repeatedly relaunched ClientRequestActivity during configuration
changes (event mask 80000000). The recreated Activity reserves a new approval
generation and reconciles an existing record. Consequently the legitimate
in-flight grant was revoked, and the client reported uncertainty instead of
receiving a usable grant. Broker evidence shows GRANTED followed by successful
REVOKED cleanup and repeated idempotent REVOKED responses.

The Android relaunch events and controller onCreate/recovery path support the
diagnosis: applying the theme interrupts the very approval operation issuing
it. This is a functional lifecycle failure; the fail-closed recovery worked.

## Verified safe end state

- The theme after the interrupted grant matched the saved pre-test value
  byte for byte.
- Client reconciliation confirmed clean state and re-enabled Request.
- Guarded stop verified broker exit and zero remaining lease artifacts.
- Guarded cleanup archived the broker log and removed the managed runtime.
- ADB was returned to UID 2000; SELinux remained Enforcing; runtime was absent.
- The operator still needs to turn the Rooted debugging setting off.

The immediate user-revoke and natural-expiry success tests were not completed;
testing stopped at the first unexpected outcome. No ColorBlendr changes ran.
Evidence is retained locally in device/logs/review-cf7a99a and the archived
broker log under device/logs.

## Bounded next repair

Separate configuration recreation from a new request or process-death recovery.
A surviving in-process operation must retain its request identity/generation
and deliver only a still-valid result to the current Activity. Recreation must
not issue the operation twice, auto-approve a new request, resurrect a grant
from preferences, or weaken revoke/reconcile generation invalidation. Actual
process death must continue to reconcile rather than infer success.

Exercise theme-triggered recreation during submission and result delivery,
plus expiry/revoke during recreation. Avoid an unverified manifest-only flag
or broad catch that merely conceals configuration changes.

Sol Medium can perform this bounded implementation work. Reserve the next
Astra pass for the changed lifecycle boundary and resumed privileged test.
