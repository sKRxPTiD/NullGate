# ColorBlendr adapter decision record

Reviewed against upstream ColorBlendr commit
`5b078e92abfa482674d82a23ce2a302d17cee756` (master reported version 3.0.1,
version code 42).

## Long-term decision

The reviewed Shizuku implementation ultimately writes the secure
`theme_customization_overlay_packages` record. NullGate's native typed theme
adapter now performs that operation directly with an exact root-owned recovery
snapshot and has passed a supervised 60-second PiXi activation/restoration test.

NullGate will therefore not require Shizuku for its product path. The intended
ColorBlendr integration is a versioned, typed client request to the NullGate
controller as specified in `CLIENT_INTEGRATION.md`. ColorBlendr supplies only a
seed and supported style; NullGate retains the privilege and cleanup boundary.
The host-only upstream patch under `integrations/patches/` now implements that
client contract. The stock application is still not presented as
NullGate-compatible until an official ColorBlendr-signed release adopts it and
NullGate reviews the release version.

## What upstream actually supports

- **Root mode** binds ColorBlendr's own `RootConnection` through libsu
  `RootService`. It assumes an ordinary app-callable `su` implementation.
  NullGate will not imitate a generic `su` binary or claim compatibility with
  this path.
- **Shizuku mode** and **Wireless ADB mode** are separate rootless paths.
  They offer a smaller feature set than root mode.
- The exported automation receiver accepts the explicit action
  `com.drdisagree.colorblendr.action.APPLY_CONFIG`, but it is disabled by the
  app unless the user enables Tasker integration. The receiver applies through
  whichever work method ColorBlendr already selected; the broadcast is not a
  replacement for root or a NullGate lease.

## NullGate decision

ColorBlendr remains the first real-world compatibility target, but the current
upstream APK is **not** registered as a ready NullGate capability adapter.
Doing so would produce a false grant: NullGate could issue a lease while
ColorBlendr still attempted to reach libsu.

The compatible implementation paths are:

1. upstream adoption of the completed typed NullGate work-method patch defined
   in `CLIENT_INTEGRATION.md` (preferred);
2. an independently implemented, narrowly scoped fabricated-overlay adapter
   that does not copy ColorBlendr's GPL implementation; or
3. the candidate temporary Shizuku adapter, after start, package-specific
   permission, revocation, and stop behavior are proven on PiXi.

Wireless ADB remains a usable ColorBlendr fallback, but it is not evidence that
the NullGate root-lease architecture works.

## Required safeguards before enabling the adapter

- pin the installed target package and signer;
- verify the supported version or protocol handshake;
- accept typed palette operations only, never shell text or arbitrary paths;
- activate only after a lease is accepted;
- reverse the privileged effect on revoke, expiry, broker shutdown, and partial
  activation failure;
- keep Shizuku optional because PiXi previously showed a device-specific
  PayPal conflict while Shizuku was present;
- treat reduced persistent-root exposure as risk reduction, not an integrity
  bypass.

The existing marker-only deployment still returns `ADAPTER_UNAVAILABLE` for
ColorBlendr and performs no privileged action. The compatibility build path can
register the candidate only with explicit Shizuku and ColorBlendr signer pins;
it has not been deployed to PiXi.
