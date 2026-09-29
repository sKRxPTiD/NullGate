# NullGate 0.3.0 private release — 2026-09-29

The manual app root switch supersedes the former temporary-theme product.
Choose an integrated app, switch root ON, make/apply changes, close it and
switch root OFF. Applied changes persist. Use the **NullGate · PiXi Root
Switch** desktop shortcut and `root-client/USAGE.md`.

## Committed source and artifact

- Source branch: `pixi-private-colorblendr`
- Source commit: `e08210cba7f213ec8590466887a92871e9653f06`
- Controller package: `org.nullprotocol.nullgate`, 0.3.0, version code 4
- Controller signer: `fc122f22e4716ba03cae81893783e437553d4a3ad21e897f4efe3367c415bafe`
- Authoritative bundle: `../releases/NullGate-PiXi-private-0.3.0-2026-09-29-e08210cba7f2.zip`
- Sidecar: the same filename followed by `.sha256`
- Bundle SHA-256: `6cd842353948486c6969a7ef4f31cb0647b01421d544f9390d0b73694bb2978f`

This record and its README/STATUS links are a documentation-only follow-up to
the source commit. The bundle's source archive and `BUILD_INFO.txt` point to
the exact implementation commit used for its builds.

## Completed checks

- Final review of signed controller authority, selected UID/signer admission,
  serialized ON/OFF, tracked shell cleanup, process-group identity/watchdog,
  fault retry, controller responses and ColorBlendr bridge.
- Two clean signed builds of the committed source produced byte-identical
  complete artifact manifests. Both ran the host regression suite, including
  root-session identity, retained effects, cleanup retry, admission race,
  protocol framing and process identity checks.
- APK version/package metadata and the existing paired signature verified.
- Shell syntax, desktop entries and 19 launcher simulations passed. The device
  helper simulation suite had passed before this final build-path review.
- The integration patch reverse-application check passed against the current
  private ColorBlendr checkout.
- ZIP integrity, outer sidecar and all 11 archived payload hashes verified.
  The archived source exactly matched `git archive` of the source commit.
- The package includes `artifacts/SHA256SUMS`, which its bootstrap helper
  consumes. Keys, password files and Git internals were excluded from the
  committed source archive.
- Prior release ZIPs/sidecars and the earlier operations-only staging retained
  their original hashes. The protected malformed untracked filename remained
  untouched and was excluded from the source commit.

Default production artifact hashes:

```text
ca01097f51ae22b39358d803585cd57ccddfce95f4a5c0e87be3057eae92cd5c  NullGate-prototype-debug.apk
0eae5e954355306df78d181cdd9d2c717f42ae01a61aee0303e8edea9bad131a  NullGate-broker.jar
```

These match the prior default 0.3.0 build. Live PiXi acceptance used the
production-mode artifacts recorded in `root-client/VALIDATION.md`, including
actual ColorBlendr root service access, OFF cleanup and retained system color.
No additional phone exercise was run during this final pass; the final review
changed build/packaging, menu defaults and documentation, not runtime Java
behavior or visual assets.

## Private deployment boundary

The paired private ColorBlendr fork remains a separate installed client; its
bridge patch is included for reconstruction. Arbitrary unmodified apps require
integration. PiXi still needs host bootstrap and Rooted debugging through clean
Stop. OFF preserves intended effects and terminates cooperative root sessions;
deliberately installed persistence is outside that mechanism. Other devices,
ROMs, reboot/theme persistence and a maintainer response are not asserted.

The final review is complete and the private release is promoted. Future
hardening and standalone bootstrap are separate development work.
