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

## Normal session

1. Plug PiXi into DoloWOLF and unlock the phone.
2. On PiXi, enable **Developer options → Rooted debugging**.
3. Double-click **NullGate · PiXi** under DoloWOLF's **All Appz** desktop folder.
4. Choose **Start ColorBlendr NullGate session**. The launcher verifies the exact device,
   Android/Lineage version, root identity, SELinux Enforcing state, controller
   signer, broker artifact hash, and process identity. It then opens NullGate on
   PiXi.
5. Choose a basic color in ColorBlendr and tap **Apply**. Review and approve the
   60-second request in NullGate.
6. To end early, open ColorBlendr Settings and switch its theming service off.
   The tested path revokes the lease and restores the exact previous theme.
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

## ColorBlendr integration

The launcher uses NullGate's typed `SYSTEM_THEME_SEED_APPLY` capability and
does not require Shizuku. The broker and controller pin the private fork's
package, version and signer; the fork pins the controller signer. ColorBlendr
receives a lease receipt, never a root shell.
