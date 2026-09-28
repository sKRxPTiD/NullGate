# NullGate reproducible-build verification — 2026-09-28

The paired PiXi build now normalizes generated archive timestamps to
2000-01-01 UTC, sorts compiler inputs, strips transient ZIP metadata, and
explicitly disables the unused v1/JAR signing scheme. APK Signature Schemes v2
and v3 remain enabled and verified with the pinned PiXi signer.

Two consecutive clean signed host builds from the same source, Android 36 tool
chain and signing identity produced this identical checksum manifest:

```text
7c5c2ed63eace24c4cc318c8833b5f1da6265abbe1ae4e4af5524dc1cce99b7d  NullGate-prototype-debug.apk
b9c22a64dc47b80a266390397cb8a081058d3e32fea857c648b9f5a2269b7e7e  NullGate-test-client-debug.apk
ebe16630f37141bee790fcdfadd1ab2b1878e4f1f5fd74d3bdd7f8e2f27536e0  NullGate-theme-client-debug.apk
d8bfb818754dea0d4d986e359e2b958056e233bb632a0ca7d00ee3f3b959fb52  NullGate-broker.jar
40dc996cea23649265de3b0ff1629dc17db385ab423dcb3266aaa849c5e6f547  controller-cert-sha256.txt
```

Run `release/verify-reproducible-build.sh` to repeat the two-pass check. It
replaces `build/` and `dist/` twice but does not contact or modify PiXi.

These are new byte identities created by deterministic packaging. The exact
controller and theme-client APKs were subsequently installed on PiXi and pulled
back byte-for-byte. The exact broker JAR was deployed. That matched set passed
visible grant, explicit revoke, natural expiry, exact restoration, client
reconciliation, zero-lease shutdown, runtime removal and return to non-root ADB.

The test-client APK remains a host/device-development harness and is not part of
the proposed private 0.2.0 operator payload. The immutable `releases/` baseline
was not changed during reproducibility or device validation.
