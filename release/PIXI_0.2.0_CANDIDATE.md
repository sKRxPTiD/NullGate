# NullGate PiXi private 0.2.0 RC2

This is a private RC2 upgrade candidate, not the immutable 0.1.0 release
baseline and not a public release. It contains the exact reproducible NullGate
controller, first-party Theme Client and broker byte identities that passed the
2026-09-28 supervised PiXi gate.

## Scope

- NullGate controller 0.2.0, version code 3
- NullGate Theme Client 0.1, version code 1
- Typed system-theme broker
- Operator launcher, guarded device helper and current operating documentation
- Host recovery hardening and its simulated failure-test record
- A tracked-source archive for the candidate commit

The NullGate Test Client is a development harness and is intentionally omitted.
ColorBlendr is unchanged and remains covered by the immutable 0.1.0 private
bundle; it is not duplicated here. No keystore, password file or other signing
secret belongs in this candidate.

## Safety boundary

The candidate is staged under ignored `dist/release-candidate/rc2/`. Staging does
not install an APK, contact PiXi, push Git, tag a commit, or write into the
authoritative `releases/` directory. Promotion requires a separate review of
the ZIP inventory, inner and outer checksums, source commit, device-pass record
and recovery boundary.

There is no unattended downgrade path. Preserve the immutable 0.1.0 bundle and
all archived pre-update APKs. Do not overwrite that baseline or infer that old
app code can safely consume newer controller data.

## Validated artifact hashes

```text
7c5c2ed63eace24c4cc318c8833b5f1da6265abbe1ae4e4af5524dc1cce99b7d  NullGate controller
ebe16630f37141bee790fcdfadd1ab2b1878e4f1f5fd74d3bdd7f8e2f27536e0  NullGate Theme Client
d8bfb818754dea0d4d986e359e2b958056e233bb632a0ca7d00ee3f3b959fb52  NullGate broker
```

The Android package and broker hashes are unchanged from the device-validated
RC1 set. RC2 updates the host operator helper and its documentation; the new
theme-receipt recovery failure paths have simulated coverage, while live PiXi
validation of those specific paths remains pending. The supervised no-lease
broker log and cleanup gate passed and is recorded in
`RECOVERY_HARDENING.md`.
