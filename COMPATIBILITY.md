# NullGate compatibility boundary

## ColorBlendr

ColorBlendr is the first test application, not NullGate's architecture. Its
current full-root mode uses libsu's `RootService`: the app asks an ordinary
app-callable `su` implementation to start ColorBlendr's own privileged Binder
service. PiXi deliberately has no such `su` binary, and NullGate will not add
one or imitate a general-purpose root shell.

ColorBlendr also supports Shizuku and wireless ADB. Those remain optional
bridges, not NullGate dependencies. The product path requires a typed NullGate
client integration or an independently implemented narrow system-theme
capability; NullGate will not expose a generic command executor to make an
unmodified app appear compatible.

Source review of ColorBlendr 3.0.1 confirmed that its non-root theme path
ultimately writes the same secure theme record handled by NullGate's proven
native adapter. The long-term integration is therefore a typed client request
for `SYSTEM_THEME_SEED_APPLY`, not a Shizuku session. See
`CLIENT_INTEGRATION.md`.

The upstream-ready client patch is implemented and host-built from the reviewed
3.0.1 commit. It adds NullGate as a foreground-only work method and fails closed
for background automation. It is not a deployable replacement APK: the live
path requires an official ColorBlendr-signed release and a corresponding
NullGate review of that release's version code.

## Shizuku constraint

Starting root-backed Shizuku is not, by itself, a per-app NullGate lease.
Shizuku has its own authorization state and process lifetime. A future adapter
must prove all three operations before it can be enabled:

1. start only the pinned, installed Shizuku package;
2. authorize only the requested target package;
3. revoke that authorization and stop the compatibility service at lease end.

Those operations exist as an off-device experiment for the pinned ColorBlendr
target, but the broker does not register it. The implementation uses fixed
process arguments, never a shell string, wildcard package, caller path, or
persistent service. It remains optional research rather than the product path.

## Integrity statement

NullGate reduces persistent root exposure. It cannot promise invisibility to
banking applications, Play Integrity, hardware attestation, bootloader checks,
or ROM-specific detection.
