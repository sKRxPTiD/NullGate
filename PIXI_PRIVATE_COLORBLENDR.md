# PiXi private ColorBlendr-NullGate gate

This branch is exclusively for Wolf's disposable PiXi test build. It does not
replace the upstream-compatible policy on `main`.

The admitted client is exact:

- package: `com.drdisagree.colorblendr`
- version code: `42001`
- version name: `v3.0.1-nullgate.1`
- signer SHA-256: `4ea5f2d0eed34de88c25f33cbf5e0874e234be5614aae48d81a8ded413724f47`
- source branch: `sKRxPTiD/ColorBlendr-NullGate:pixi-private-nullgate`

The machine-readable matched-set record is
`compat/pixi-private-pins.properties`. The ordinary `compat/pins.properties`
continues to describe the untouched upstream/official compatibility target.

The APK is a local test artifact, not a public ColorBlendr release. Android
must remove the differently signed upstream installation before this same
package name can be installed. Wolf has stated that the existing ColorBlendr
data is disposable.

The controller must be built with PiXi's existing paired NullGate signing
identity. The client pins that controller certificate; a controller signed by a
different local development key is rejected.

The final privileged review and supervised PiXi run passed on 2026-09-25. One
60-second native theme lease was granted and explicitly revoked, the exact prior
theme was restored, the broker stopped, the runtime was removed, and ADB returned
to non-root. See the deployment bundle's `LIVE_TEST_PASS.md` for the evidence.
