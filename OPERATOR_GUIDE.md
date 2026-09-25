# NullGate operator guide — PiXi prototype

## What works now

NullGate can run a temporary root broker launched from DoloWOLF, issue its
built-in marker lease, and apply a native 60-second system-theme lease with
exact restoration. A separate ColorBlendr compatibility mode is built and
host-tested for the pinned official Shizuku release, but it has not passed its
PiXi deployment gate and must not be treated as operational yet.

The separate NullGate Test Client is also built but not installed. Its guarded
installer uses the dedicated `NULLGATE_TEST_CLIENT_V1` acknowledgement and
refuses installation while a broker runtime exists. The ordinary marker-test
token cannot install it. See `DEPLOYMENT_GATE.md` before any device use.

## Normal session

1. Plug PiXi into DoloWOLF and unlock the phone.
2. On PiXi, enable **Developer options → Rooted debugging**.
3. Double-click **NullGate · PiXi** under DoloWOLF's **All Appz** desktop folder.
4. Choose **Start NullGate session**. The launcher verifies the exact device,
   Android/Lineage version, root identity, SELinux Enforcing state, controller
   signer, broker artifact hash, and process identity. It then opens NullGate on
   PiXi.
5. The marker session accepts only **Run 60-second safe broker self-test**.
   ColorBlendr requires the separately gated compatibility launcher action.
6. To end early, use **Revoke pending request now** and wait for `REVOKED` in
   the local audit log. Otherwise, the marker self-test expires after 60 seconds.
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
markers, and unknown process state.

If recovery refuses, stop there and return to Codex. Do not manually delete
`/data/local/tmp/nullgate`.

## Command path

The same controls are available in a DoloWOLF terminal:

```bash
nullgate-pixi status
nullgate-pixi start
nullgate-pixi colorblendr
nullgate-pixi stop
nullgate-pixi recover
```

The desktop launcher and command both use the same guarded implementation.

## ColorBlendr compatibility gate

The launcher offers **Start ColorBlendr compatibility**, but deliberately
refuses PiXi while `Shi.bequiet` is installed or the pinned official package
`moe.shizuku.privileged.api` is absent. NullGate never removes or substitutes a
manager automatically. The compatibility broker pins both manager and target
signers, accepts only `SHIZUKU_SESSION_START` for ColorBlendr, refuses an
existing server or authorization, and must prove revocation and server teardown.

PiXi currently has `Shi.bequiet`, which owns the Shizuku permission namespace.
The live ColorBlendr test is therefore blocked pending an explicit decision to
preserve and remove that app before installing official Shizuku.
