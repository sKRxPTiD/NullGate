# DoloWOLF launcher setup

This installs the local desktop and command launchers on DoloWOLF. It does not
install or change an Android app, start ADB, enable Rooted debugging, or contact
PiXi.

## Requirements

- The active NullGate source checkout at the canonical project path.
- Android platform-tools (`adb`).
- Android SDK Build Tools 36.0.0 (`apksigner` and `aapt2`), normally under
  `~/Android/Sdk`. Set `ANDROID_HOME` or `APKSIGNER_BIN` / `AAPT2_BIN` when the
  SDK is elsewhere.
- `desktop-file-utils` for validating the desktop entry.
- Zenity is optional. The desktop entry opens a terminal so the fallback menu
  remains visible when Zenity is absent.

## Check, then install

From the active source repository, validate the launcher and local prerequisites
without installing files:

```bash
bash ./device/install-dolowolf-launcher.sh --check
```

Then install the command at `~/.local/bin/nullgate-pixi`, the desktop entry in
`~/Desktop/All Appz`, and the icon in the user's hicolor icon theme:

```bash
bash ./device/install-dolowolf-launcher.sh
```

The installer uses the current user's home directory. Override the launcher or
desktop directory with `NULLGATE_LAUNCHER_TARGET` or `NULLGATE_DESKTOP_DIR` if
needed. It validates both shell scripts and the generated desktop entry before
writing the three launcher files. Run it again after updating the active source;
the desktop command is local, while the launcher uses the canonical source
checkout for its guarded helper and identity pins.

## First read-only check

Connect PiXi by USB, unlock her, and approve the normal USB debugging prompt.
Leave Rooted debugging off. Run:

```bash
nullgate-pixi doctor
```

This checks the connected device identity; installed controller, theme-client
and ColorBlendr signers and versions; ordinary non-root ADB; and absence of the
managed broker runtime. It performs no device writes. A healthy result is the
starting point for an authorized session; it does not install or update an APK.

## Session controls

Use `nullgate-pixi status` for status. Enable Rooted debugging on PiXi only for
the supervised Start, Stop, or Recovery window. Choose the ColorBlendr session
or the first-party Theme Client session; both start the same typed system-theme
broker and open only the selected client. The user reviews every lease. Stop
confirms broker shutdown, zero leases, log preservation and runtime removal,
then returns ADB to non-root. Turn Rooted debugging off afterward.

If Start, Stop, or Recovery reports `UNKNOWN`, preserve the runtime and logs and
stop. Do not manually remove files under `/data/local/tmp/nullgate`. Follow
`OPERATOR_GUIDE.md` and `DEPLOYMENT_GATE.md` for the recovery and device-test
boundaries.

Recovery also stops if snapshot or log hashes cannot be verified, if host
archival fails, or if a broker appears during recovery. A normal-looking theme
does not prove cleanup succeeded: restoration may precede an archive failure.
Keep the remaining receipts, runtime and partial host archives for inspection.
The current host helper's hardening and its validation limits are recorded in
`RECOVERY_HARDENING_2026-09-28.md`; the preserved RC1 bundle retains its original
helper.

The NullGate 0.2.0 controller and first-party theme client passed their
supervised PiXi gate. Their guarded installers remain separate from this
launcher setup and still require explicit mutation acknowledgements, a pinned
serial, matched signers, and an absent broker/runtime. Installing the DoloWOLF
launcher never installs or updates either Android package.
