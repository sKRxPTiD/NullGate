# NullGate 0.2.0 candidate staging receipt — 2026-09-28

## Result

The PiXi private 0.2.0 release candidate was staged and audited successfully.
It remains an ignored local candidate and has not been promoted into the
immutable `releases/` baseline.

## Candidate identity

- Bundle: `dist/release-candidate/NullGate-PiXi-private-0.2.0-rc1-2026-09-28.zip`
- SHA-256: `0d6164f1ae5401bbea33ed935e068523ac157c9df97446e9b1825d03be9d88da`
- Source branch: `pixi-private-colorblendr`
- Source commit: `fb340887d685169bbda98e64f3e681b9984ea008`
- Source archive: `NullGate-pixi-private-0.2.0-fb340887d685.tar.gz`

## Audit completed

- The outer checksum sidecar verifies.
- The ZIP integrity test reports no errors.
- Every file listed by the inner `SHA256SUMS` verifies without extracting the
  bundle.
- The bundle contains the deterministic controller, Theme Client and broker
  identities that passed the supervised PiXi device gate.
- The tracked-source archive inventory was inspected without extraction.
- No key, keystore, password or password-file path is present in the bundle.
- The candidate packager refuses to overwrite an existing staged candidate.
- The development Test Client and unchanged ColorBlendr APK are intentionally
  omitted, as documented in `release/PIXI_0.2.0_CANDIDATE.md`.

## Preserved boundaries

The verified 0.1.0 release baseline was not modified. Its preserved identities
remain:

```text
bc5a9e52570eccc99109e9032101845de9402438845ae8eb3a5c4a98fa4d6125  NullGate-PiXi-private-2026-09-26.zip
d118571d4dc33ffb5e9e94fcd7e2951f968364d6dced54a34c24232f8def072a  NullGate-PiXi-private-2026-09-26.zip.sha256
```

The malformed protected untracked filename was not modified or removed. The
incomplete first staging attempt was moved intact to
`/tmp/nullgate-partial-candidate.mbgclz/release-candidate`; it is not an
authoritative artifact and remains recoverable for the lifetime of that
temporary storage.

Rooted debugging was switched off after the completed device gate. The final
verified shutdown state was ordinary non-root ADB, enforcing SELinux, no active
lease and no managed broker runtime.

## Promotion gate

No promotion, Git push or public release has occurred. Copying this candidate
into the authoritative `releases/` directory requires separate explicit
authorization.
