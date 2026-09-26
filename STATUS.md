# NullGate status — 2026-09-26

## PiXi private MVP: complete

NullGate 0.1.0 is installed on PiXi as a matched private deployment. The typed
system-theme capability has passed real grant, explicit revoke, automatic
60-second expiry, exact restoration, broker shutdown and runtime removal with
the private ColorBlendr fork. The controller and client identities are pinned.
The ordinary idle state is non-root ADB with no broker runtime.

ColorBlendr is the first client, not NullGate's architecture. New clients use
the same broker contract, identity checks, lease rules and audit decisions, but
each new capability still needs a narrow adapter and its own restoration test.

## What is not required to use the PiXi MVP

- The upstream ColorBlendr maintainer does not need to merge the proposal.
- Shizuku is not required for the native theme capability.
- Astra is not required for routine builds, documentation or normal operation.
- No permanent app-callable `su` is installed.

## Remaining product work

1. Stabilization: use the current matched build and preserve any failure logs.
2. Release engineering: replace the local development signing identity with a
   deliberately managed release identity before public distribution.
3. Resilience: power loss, kernel failure or a forcibly killed broker can delay
   cleanup; the guarded host recovery path remains part of the safety model.
4. Generalization: add the next client only by defining a typed capability,
   bounded adapter, exact cleanup proof and device test. NullGate never grants
   a generic shell just because one client passed.
5. Public product work: installer, user-facing onboarding, release support and
   broader device/ROM testing are separate from the working PiXi deployment.

## Optional public path

The upstream ColorBlendr proposal is useful for official-signer adoption but
is not a dependency of the private PiXi deployment. Waiting for its maintainer
does not block NullGate development or use.

## Current operating gate

Normal sessions start from DoloWOLF's **NullGate · PiXi** launcher. Rooted
debugging is enabled only for a session and switched off after guarded Stop.
An UNKNOWN state is never treated as clean; use the launcher recovery path and
preserve evidence instead of manually deleting the runtime.
