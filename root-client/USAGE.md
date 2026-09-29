# NullGate root switch: simple directions

The production root-switch build is installed as org.nullprotocol.nullgate
version 0.3.0. Open its NullGate screen containing **Choose app**,
**Root broker** and **Open selected app**.

DoloWOLF's **NullGate · PiXi Root Switch** desktop shortcut opens a simple menu
for Start, Open, Status, OFF, Stop and these directions. It uses the same
microchip icon. **NullGate · PiXi** remains the legacy temporary-theme launcher.
No build runs from the root-switch menu. The separate Root Candidate (Debug)
shortcut is only for development checks.

## Start after reboot

Connect PiXi to DoloWOLF. Enable USB debugging and Rooted debugging for bootstrap.
From the canonical source repository run:

```sh
cd '/mnt/TerraDrive/Null Protocol/Apps/NullGate/source'
bash device/nullgate-root-session.sh start
```

The helper opens the new controller with root OFF. If it reports that the
broker already exists, use `status`, not a second start. A missing or unresolved
broker is not a confirmed OFF state. Preserve its runtime evidence on failure.
When using the separately staged 0.3.0 operations bundle, set
`NULLGATE_ROOT_DIST` to its extracted `artifacts/` directory before `start`;
the helper still reads the trusted signer pin from this canonical source tree.

Keep Rooted debugging/rooted ADB enabled until you Stop the broker. PiXi kills
this host-started broker when adbd restarts as non-root; switching debugging off
early is not a supported way to end a session. If rooted debugging is enabled
but ADB reports a non-root shell, `adb -s 54110DLAQ0043W root` activates the
already permitted rooted ADB service; it does not enable PiXi's settings toggle.

## Make a change

1. In NullGate, choose **ColorBlendr — NullGate** (`com.drdisagree.colorblendr`).
2. Turn **Root broker** ON. Wait for confirmed **Root access: ON**.
3. Press **Open selected app**.
4. In ColorBlendr use its **Root** work method, not the legacy NullGate theme
   lease method. Choose your colors/style, then press **Apply** to save the
   preview. If a root permission choice appears, accept it while NullGate is ON.
5. Close ColorBlendr and return to NullGate.
6. Turn **Root broker** OFF. Wait for confirmed **Root access: OFF**.
7. Close NullGate. Your applied colors stay. There is no countdown or restore step.

ON restarts the selected app when opened, clearing cached root failures.
OFF also closes it to terminate its existing root service. To change the
colors again, turn ON and reopen ColorBlendr.

Choose an app you trust with root. Other apps need the NullGate root bridge;
an arbitrary APK expecting a global `su` binary is not automatically compatible.
OFF revokes cooperative processes; it does not undo changes or prevent a
malicious root app from deliberately installing persistence.

## Stop the broker completely

```sh
bash device/nullgate-root-session.sh stop
```

This switches root OFF and shuts down the host-started broker. It does **not**
restore your theme. Turn Rooted debugging off in PiXi's settings afterward.
The broker must be bootstrapped again for the next session.

If OFF reports FAULT or the broker becomes unreachable, do not assume cleanup.
Use `off` to retry while it is reachable. If it died, inspect the preserved
runtime; `recover` only archives a stale receipt after proving the recorded
PID and broker are absent. It is not a general root-process cleanup command.

## Build paths

The controller's paired signing identity is reused, never regenerated:

```sh
bash release/build-pixi-private.sh
```

For the locally patched private ColorBlendr checkout (Android SDK 37.1 and its
dependencies must already be available), build without downloading tools:

```sh
cd '/mnt/TerraDrive/Null Protocol/Apps/NullGate/source/integrations/colorblendr-upstream'
ANDROID_HOME=/home/wolf/Android/Sdk bash ./gradlew --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

The private fork APK is `app/build/outputs/apk/debug/ColorBlendr v3.0.1-nullgate-root.2.apk`.
Use the existing matched signing identity; check the signer pin in the canonical
repository before installing an update. Do not uninstall or clear app data.
The exported patch and base commit are recorded in `README.md` beside this guide.
