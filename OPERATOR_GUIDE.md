# NullGate operator guide — PiXi prototype

## What works now

NullGate can run a temporary root broker launched from DoloWOLF and grant the
installed private ColorBlendr fork a native 60-second system-theme lease with
exact restoration. ColorBlendr's grant and explicit-revoke path passed its
supervised PiXi deployment gate on 2026-09-25.

The separate NullGate Test Client is installed on PiXi. Its supervised typed
theme workflow passed grant, revoke, expiry and exact restoration after the
lifecycle repair; see `DEVICE_TEST_PASS_2026-09-25.md`.
Its guarded
installer uses the dedicated `NULLGATE_TEST_CLIENT_V1` acknowledgement and
refuses installation while a broker runtime exists. The ordinary marker-test
token cannot install it. See `DEPLOYMENT_GATE.md` before any device use.

The 0.2.0 controller and first-party NullGate Theme Client are also installed
as a matched pair. Their supervised two-minute theme workflow passed visible
approval, explicit revoke, natural expiry, exact restoration, reconciliation,
broker shutdown and runtime removal on 2026-09-28. See
`DEVICE_TEST_PASS_2026-09-28.md`.

## Normal session

1. Plug PiXi into DoloWOLF and unlock the phone.
2. On PiXi, enable **Developer options → Rooted debugging**.
3. Double-click **NullGate · PiXi** under DoloWOLF's **All Appz** desktop folder.
4. Choose **Start ColorBlendr NullGate session** or **Start Theme Client
   NullGate session**. The launcher verifies the exact device, Android/Lineage
   version, root identity, SELinux Enforcing state, controller signer, broker
   artifact hash and process identity. It then opens NullGate and the selected
   client on PiXi.
5. For ColorBlendr, enable theming or apply a color and approve the 60-second
   request. For Theme Client, choose a bounded palette/style, request the
   two-minute lease and approve it. Every request uses NullGate's protected
   approval screen.
6. To end early, disable ColorBlendr theming or choose **Restore previous theme
   now** in Theme Client. If left alone, the broker restores automatically at
   expiry; Theme Client then requires reconciliation to collect the terminal
   cleanup result.
7. Open **NullGate · PiXi** on DoloWOLF again and choose
   **Stop and clean session**.
8. Wait for the clean-session confirmation, then turn **Rooted debugging off**
   on PiXi. USB debugging may remain in its normal configuration.

Keep PiXi connected until Stop and Clean finishes. The broker has a hard
15-minute lifetime, but expiry alone does not remove its archived runtime files;
the launcher still needs to reconcile them.

## If something was interrupted

Choose **Show status** first. Never assume an empty-looking app means the root
broker is gone. If the broker has stopped or its 15-minute lifetime expired but
the runtime remains, choose **Recover verified stale runtime**. Recovery refuses
a live process, unsafe ownership or permissions, unexpected files, malformed
markers, PID reuse, and unknown process state. A stale root-owned PID receipt
left by a forced broker exit is revalidated and removed only after recovery is
otherwise complete. Recovery restores from the validated theme snapshot when
one exists, or uses the no-lease cleanup path when no snapshot was ever created.

If recovery refuses, stop there and return to Codex. Do not manually delete
`/data/local/tmp/nullgate`.

An unresolved controller record can remain even after the root runtime was
independently verified absent, as observed during the 0.1.0 to 0.2.0 upgrade.
That record must not be cleared merely because its elapsed deadline passed or a
fresh broker reports `NOT_FOUND`. The host-only `recover-controller-record`
action is the narrow recovery path: it requires the exact record hash, lease ID,
client package and independently recorded restored-theme hash. It stops the
controller, verifies the reviewed controller version and signer, requires no
broker or runtime, rejects a live/unexpired/unsafe record, archives the exact
record under `device/logs`, and removes only that verified preference file.
The archive is hash-verified after writing; TerraDrive's FUSE mount may expose
uniform permission bits rather than honoring mode 0600.

## Command path

The same controls are available in a DoloWOLF terminal:

```bash
nullgate-pixi status
nullgate-pixi doctor
nullgate-pixi start
nullgate-pixi colorblendr
nullgate-pixi theme
nullgate-pixi stop
nullgate-pixi recover
```

The desktop launcher and command both use the same guarded implementation.
Its tracked source is `device/nullgate-pixi`. Reinstall or repair the DoloWOLF
launcher with `device/install-dolowolf-launcher.sh`.

For prerequisites, the no-write setup check, installation paths, and first
read-only connection check, see `INSTALL_AND_ONBOARD.md`.

**Run read-only health check** (or `nullgate-pixi doctor`) verifies ordinary
ADB, the installed controller/theme-client/ColorBlendr versions and signing
certificates, and an absent runtime. It does not enable root or write to PiXi.

For a terminal-only health check without a desktop dialog, run
`NULLGATE_NO_DIALOG=1 nullgate-pixi doctor`.

## ColorBlendr integration

The launcher uses NullGate's typed `SYSTEM_THEME_SEED_APPLY` capability and
does not require Shizuku. The broker and controller pin the private fork's
package, version and signer; the fork pins the controller signer. ColorBlendr
receives a lease receipt, never a root shell.
