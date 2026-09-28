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

This checks the connected device identity, installed controller and ColorBlendr
signers and versions, ordinary non-root ADB, and absence of the managed broker
runtime. It performs no device writes. A healthy result is the starting point
for an authorized session; it does not install or update either APK.

## Session controls

Use `nullgate-pixi status` for status. Enable Rooted debugging on PiXi only for
the supervised Start, Stop, or Recovery window. Start opens the controller and
ColorBlendr; the user reviews every typed lease. Stop confirms broker shutdown,
zero leases, log preservation, runtime removal, and returns ADB to non-root.
Turn Rooted debugging off afterward.

If Start, Stop, or Recovery reports `UNKNOWN`, preserve the runtime and logs and
stop. Do not manually remove files under `/data/local/tmp/nullgate`. Follow
`OPERATOR_GUIDE.md` and `DEPLOYMENT_GATE.md` for the recovery and device-test
boundaries.

The NullGate 0.2.0 theme-client APK is a host-verified development artifact.
Its guarded installer is `install-theme-client` and requires the separate
`NULLGATE_THEME_CLIENT_V1` mutation acknowledgement, an explicitly pinned
device serial, the paired controller signer, an absent broker process, and an
absent managed runtime. This setup guide does not itself authorize installation
or live theme testing.
