# NullGate private 0.2.0 RC2 promotion — 2026-09-28

The audited RC2 ZIP and checksum sidecar were copied byte-for-byte from staging
to the authoritative `releases/` directory. This is a private release
candidate, not a public release or Git tag.

```text
9375f5b501ff5097869289b784380b17946883e59936d7fddef7b9261920f123  NullGate-PiXi-private-0.2.0-rc2-2026-09-28.zip
ba4a4bb5ad4a52779c0635e1a90372f548eb02e29642ff058fcb4a29543432b4  NullGate-PiXi-private-0.2.0-rc2-2026-09-28.zip.sha256
```

The outer checksum and all inner checksums passed, ZIP integrity passed, and a
secret-like path scan found no signing keys or password files. The promoted
files match the staged copies byte-for-byte.

RC2 source commit: `fc7daeabf21f395f69d8c0f19cfeba107523706e` on
`pixi-private-colorblendr`. Two consecutive signed host builds were byte
identical and retained the PiXi-validated controller, Theme Client and broker
hashes. The helper and beginner operator guide include later host recovery
hardening. A no-lease broker cleanup passed on PiXi; the new stale theme-receipt
recovery branches remain simulation-tested and have not passed a live
interrupted-session rehearsal.

The 0.1.0 ZIP and sidecar and the previously promoted RC1 ZIP remain unchanged.
No APK was reinstalled and no theme lease was run for this promotion.
