# PiXi private ColorBlendr-NullGate gate

This branch is exclusively for Wolf's disposable PiXi test build. It does not
replace the upstream-compatible policy on `main`.

The admitted client is exact:

- package: `com.drdisagree.colorblendr`
- version code: `42001`
- version name: `v3.0.1-nullgate.1`
- signer SHA-256: `4ea5f2d0eed34de88c25f33cbf5e0874e234be5614aae48d81a8ded413724f47`
- source branch: `sKRxPTiD/ColorBlendr-NullGate:pixi-private-nullgate`

The APK is a local test artifact, not a public ColorBlendr release. Android
must remove the differently signed upstream installation before this same
package name can be installed. Wolf has stated that the existing ColorBlendr
data is disposable.

The controller must be built with PiXi's existing paired NullGate signing
identity. The client pins that controller certificate; a controller signed by a
different local development key is rejected.

Deployment remains gated on the final privileged review. The supervised run is
limited to one 60-second native theme lease, immediate revoke, exact restoration,
broker shutdown, runtime cleanup, return to non-root ADB, and disabling Rooted
debugging. Stop on any `UNKNOWN` or cleanup failure.
