# NullGate PiXi private 0.3.0 release

This additive private bundle contains the signed NullGate 0.3.0 controller,
its root broker, pinned signer fingerprint, operator directions, integration
patch, validation record and an archive of the exact committed source.
Signing secrets are excluded.

The controller is built from the canonical `pixi-private-colorblendr`
commit recorded in `BUILD_INFO.txt`. Packaging requires committed source;
the only permitted untracked item is the preserved malformed historical
checksum filename. The live phone validation is recorded
in `operator/VALIDATION.md`. Existing `releases/` bundles are untouched.

## Start a session

Extract this archive. From the canonical source checkout, use the bundled
artifacts as the helper's verified input:

```sh
NULLGATE_ROOT_DIST='/path/to/extracted/NullGate-PiXi-private-0.3.0/artifacts' \
  bash device/nullgate-root-session.sh start
```

The helper still reads the pinned controller signer from the canonical source
checkout. PiXi must be connected with USB debugging and the user's Rooted
debugging setting enabled. The helper bootstraps root ADB, starts the broker
OFF, and verifies the production package/signature before installation. Keep
Rooted debugging enabled until `stop` confirms clean shutdown and non-root ADB;
then disable the setting manually on PiXi.

In NullGate, choose the integrated app, turn **Root broker** ON, open the app,
make and apply changes, close the app, and turn root OFF. Applied changes stay.
The included integration targets the private ColorBlendr fork; arbitrary APKs
do not become compatible merely by selecting them.

Verify the outer checksum sidecar and the archive's `SHA256SUMS` before use.
The `artifacts/` directory has its own manifest for the session helper.
Rebuild the source archive using the existing paired signing identity; signing
credentials remain external to the archive. The release gate compares two
clean signed builds before packaging. No phone tests are run by the packager.
