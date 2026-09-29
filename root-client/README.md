# Root-switch implementation and operating limits

NullGate 0.3.0 follows Wolf's project directive in the source-root `AGENTS.md`:
choose app, switch root ON, open the app, make changes, close it, switch root OFF.
Applied changes persist. The broker does not snapshot or restore theme settings.

## Current boundary

The signed controller authenticates to `nullgate-root-control-v1`. ON resolves
the selected installed package to its sole UID and signing identity. New shell
connections on `nullgate-root-shell-v1` must match that identity. OFF stops the
selected app, closes its root shells, kills their process groups, and verifies
the groups no longer contain running processes. Cleanup failure is displayed
as FAULT and can be retried with OFF.

ON also stops an old instance of the chosen app so reopening it cannot retain
a cached connection failure from when the switch was OFF. There is no approval
screen for individual commands and no theme lease timer.

`RootShellMain` is an unprivileged Java `app_process` trampoline. It forwards
stdin, stdout, stderr and exit status to an admitted root shell. A libsu 6.0.0
client configures `Shell.Builder.setCommands` to launch that trampoline from
its own APK. ColorBlendr can then use its existing RootService and root
operations. No global `su` binary, Shizuku installation or native bridge
compiler is required. The library's supported configuration is documented in
[libsu's Shell.Builder API](https://topjohnwu.github.io/libsu/com/topjohnwu/superuser/Shell.Builder.html).

Each root shell owns a new Linux session and process group. Its watchdog checks
the broker PID and process start time, then kills that group if the broker
disappears. RootService's ordinary background process inherits the group.
This cleanup applies to cooperative clients. Code that has root can deliberately
escape its group or install persistence; OFF cannot undo such actions. Choose
apps you trust with root. Intended app changes, including themes, remain.

## Bootstrap and build

PiXi currently has rooted ADB and no app-callable `su`. DoloWOLF must therefore
start the broker after a reboot or broker shutdown. The phone UI then controls
the selected app without a host command for every ON/OFF action. **Keep rooted
ADB enabled during the session:** restarting adbd as non-root killed this
host-started broker on PiXi, leaving a stale PID receipt. Stop the broker cleanly
before disabling Rooted debugging. An entirely standalone bootstrap/lifetime
would require another device root authority.

Build the production controller with its paired signing identity:

```sh
bash release/build-pixi-private.sh
bash device/nullgate-root-session.sh start
```

The normal output is in `dist/`. Start installs the signed production package,
starts the broker OFF, and opens the phone controller. Status, OFF, Stop and
guarded stale-receipt recovery use that same helper. See `USAGE.md`.

For separate development checks:

```sh
bash release/build-pixi-private.sh --root-session-candidate
```

Outputs are in `dist/root-session-candidate/` and `build/root-session-candidate/`.
The candidate controller uses package `org.nullprotocol.nullgate.rootcandidate`
to allow testing alongside the installed controller. It is debuggable for
authenticated `run-as` checks. It is not the production package or a promoted
release bundle.

With PiXi connected and rooted ADB already available:

```sh
bash device/nullgate-root-candidate.sh start
```

The helper verifies artifacts and the paired signer, installs the separate
candidate controller, starts the broker in `/data/local/tmp/nullgate-root`,
and opens the phone's root-switch screen. It does not change the Rooted
debugging toggle. It preserves the legacy runtime and release bundles.

Other helper actions are `status`, `off`, and `stop`. `stop` shuts down the
broker as well as removing app access. `recover` archives a stale PID receipt
only after confirming that its recorded PID and broker process are absent.

## ColorBlendr integration

The working client checkout is an ignored development dependency under
`integrations/colorblendr-upstream`, based on the private fork's
`pixi-private-nullgate` commit `630028f4267218b6d894395ba0a9e1aa4869de6a`.
The source repository keeps the exported integration patch and this contract.
The client selects the existing ROOT work method, with its libsu shell pointed
at NullGate. The old temporary-theme NULLGATE choice is migrated to ROOT.

The maintainer request is a separate external coordination item. Local private
integration work does not imply an upstream response, acceptance or official
release.

## Verified on PiXi

- Default production build and version 3→4 signed upgrade.
- Selected app gets UID 0 only while ON; another app UID is denied.
- Phone ON/Open/OFF terminates the live root service and leaves the applied
  color intact.
- Abrupt broker death triggers process-group cleanup and guarded receipt recovery.
- Clean host Stop returns ADB to non-root.
- Existing colors and microchip resources remain unchanged.

See `VALIDATION.md` for recorded results and any remaining gate.
