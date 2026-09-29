# NullGate private 0.2.0 RC3 promotion

RC3 was staged by `release/package-pixi-0.2.0-rc3-candidate.sh`, audited, and
copied byte-for-byte into the authoritative `releases/` directory as an
additive private candidate. It is not a public release or Git tag.

```text
a198974db39de29e78dc417bd37e35e2fdb11e900b9370bc729c640a92ba7dc8  NullGate-PiXi-private-0.2.0-rc3-2026-09-28.zip
a0c9796fb9a01352b513ef6fcb7115b7685228807c8af84a68cfed61f7191cb1  NullGate-PiXi-private-0.2.0-rc3-2026-09-28.zip.sha256
```

The ZIP integrity check passed; every one of the 19 inner files matched
`SHA256SUMS`; the embedded source commit and pinned signer were verified; and
the bundle path inventory contains no signing keys or password files. The
promoted ZIP and sidecar match the staged bytes. RC1, RC2 and 0.1.0 checksum
sidecars were rechecked and still pass.

Source commit: `d82e99bd9d0bb3e0df811f6b7233cabc18e88a4b` on
`pixi-private-colorblendr`. The updated Theme Client SHA-256 is
`b999f127f10caf934a0be2f8943e62c0faa81d2464f06c7a45c02e6f5e4c91d9`;
controller and broker hashes match the PiXi-validated RC2 set. Two clean signed
builds were byte-identical. Host Java checks, launcher regressions, and the
simulated helper suite passed. The supervised PiXi recovery and normal-expiry
records are included in RC3. Rooted debugging is a separate phone setting;
ADB was returned to ordinary shell and the final doctor passed.

The original 0.1.0 baseline and previous RC1/RC2 files were not overwritten.
