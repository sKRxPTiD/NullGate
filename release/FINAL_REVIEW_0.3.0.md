# NullGate 0.3.0 final source review — 2026-09-29

The active product replaces the temporary-theme workflow: select an integrated
app, switch root ON, apply changes, close the app, then switch root OFF.
OFF revokes root access and retains the applied changes. Existing colors and
microchip/circuit resources are retained.

## Review result

No additional release blocker was found in the reviewed root-session engine,
socket protocols, Android process backend, controller transport/UI or private
ColorBlendr bridge. Controller requests authenticate the signed package and
peer UID. ON resolves the selected app's current sole signer/UID; each shell
must match that identity. Shell creation and OFF share the engine lock.
OFF blocks admission, force-stops the client, closes its sockets and terminates
verified root process groups. Failed cleanup stays FAULT and supports retry.
Process-group receipts include process start time; the watchdog observes the
broker's identity. No theme rollback is performed.

Final review corrected three release-path defects:

- Default build cleanup deleted the whole distribution tree, including staged
  release packages. Cleanup now targets only named generated products.
- The earlier operations package lacked the artifact manifest consumed by
  the startup helper. Final packaging includes `artifacts/SHA256SUMS`.
- The earlier bundle recorded an uncommitted base. Final packaging requires
  committed source, records its commit and includes that source archive.

The ordinary root menu now selects the production controller; the debug
desktop entry explicitly selects the separate candidate package.

## Release gate

Run the signed build twice from the committed source and compare the complete
artifact manifests, then package and validate the archive, its internal
checksums, source archive, signer and version. Publication evidence records the
actual source commit and bundle digest separately. Preserve prior staged
packages and historical releases. Signing credentials and the malformed
historical filename are excluded from the committed source archive.

The existing live PiXi record covers actual root service admission, wrong-UID
denial, OFF/cleanup, retained system color and interrupted-broker recovery.
This final pass changes build/packaging and operator paths, not root runtime
behavior; it does not require another phone exercise.

## Operating limits

PiXi requires host bootstrap and Rooted debugging through clean Stop. Other
apps require the bridge. Root-enabled apps must be trusted: deliberate process
group escape or installed persistence is outside cooperative OFF cleanup.
Persistence of the selected color after OFF is verified; reboot/wallpaper
changes and other ROMs are not covered. The upstream ColorBlendr maintainer
request remains an unresolved external coordination item.
