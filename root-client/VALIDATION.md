# Manual root-switch production validation — 2026-09-29

Scope: private PiXi/tokay, Lineage Android 16, SELinux Enforcing. Production
controller package `org.nullprotocol.nullgate`, version 0.3.0/code 4, upgraded
in place from paired 0.2.0/code 3 without clearing app data. Private ColorBlendr
package `com.drdisagree.colorblendr`, version code 42002.

## Recorded results

| Check | Result |
| --- | --- |
| Host compilation and engine/protocol tests | Passed, including ON/OFF serialization, retained effects, denied identities and cleanup retry |
| Candidate controller and fork builds | Passed using existing signing identities |
| Production default build | Passed; version 0.3.0/code 4, launcher is RootControlActivity, debuggable=false |
| Production upgrade over installed controller | Passed from code 3 to code 4 with the pinned paired signer |
| Standard production build host/device-helper suite | Passed: external-client, lease, UI-operation, request-rate, theme-client, broker, protocol, security, root-session and device-helper regressions |
| Desktop launcher | Production `NullGate · PiXi Root Switch` entry validates and runs the correct package menu |
| Real libsu RootService through NullGate | Passed: ColorBlendr's actual provider returned UID 0; service PID 9232 |
| OFF with a live Binder root service | Passed: OFF/zero shells, PID 9232 absent; instrumentation ended because force-stop is intentional |
| Wrong app UID while ColorBlendr selected ON | Denied: candidate-controller UID could not open a root shell |
| Actual ColorBlendr provider while OFF | Denied: `RootService unavailable`; no service admitted |
| Existing shell child on OFF | Passed: earlier child PID 5952 absent after OFF |
| Abrupt broker death | Passed: broker PID 5312 killed in owned test; child PID 6849 terminated by watchdog |
| Stale receipt recovery | Passed: receipt archived after absent-PID and absent-broker checks; restart began OFF |
| Normal ColorBlendr color screen and Apply | Passed through the root service; applying preview completed |
| Applied system color after OFF | Passed: `android:color/system_accent1_500` changed from `#ffae6438` to `#ff0d8679`; stayed `#ff0d8679` after OFF |
| Live root service after color application/OFF | PID 9592 absent; OFF with zero shells confirmed |
| Applied overlays after OFF | ColorBlendr framework/settings overlays remained enabled; no theme restoration performed |
| Original circuit/microchip PNG assets | All five recorded header/audit/icon hashes unchanged |
| Saved client integration patch | Reverse apply check passed against the working fork |
| Phone controller switch and Open button | ON → open actual ColorBlendr root service → OFF passed; service PID 11796 absent and applied color retained |
| Final host checks | Broker suite and root checks passed; source metadata and diff checks passed; fork debug unit tests passed |
| Non-root ADB transition with an idle broker | Broker ended; UI correctly reported unavailable/unknown, not OFF. Stale PID 7311 required guarded recovery |
| Final guarded recovery and clean shutdown | Stale receipt archived, restarted OFF, SHUTDOWN confirmed OFF, PID receipt absent, applied color retained; ADB returned to UID 2000 |
| Production host OFF and Stop | Phone OFF returned zero shells; host Stop shut broker down, verified PID receipt absent, and returned ADB to UID 2000 |

The standard libsu `RootService.bind()` rejects a no-su device before consulting
the custom shell. The private fork now verifies its brokered shell, then uses
the supported `bindOrTask()` API. The real provider, not a replacement mock,
was exercised. A per-provider connection latch replaces the stale global latch.

## Artifact identities at live test

```text
Controller APK SHA-256:
2a842041957ec4079c2f65cf5f81f3b6f293a6ca0228e68e271f51c6f2ad5301
Broker JAR SHA-256:
8aa28d15591dffb2bb124891b7601d1f04a156f2d4abb08d7647bdbfde2431db
ColorBlendr APK SHA-256:
c799b5923bade50e696ba008895d70d66815b10300f104ce1cc979cb5996b343
Controller certificate SHA-256:
fc122f22e4716ba03cae81893783e437553d4a3ad21e897f4efe3367c415bafe
ColorBlendr certificate SHA-256:
4ea5f2d0eed34de88c25f33cbf5e0874e234be5614aae48d81a8ded413724f47
```

These are public artifact/certificate digests, not signing secrets. Canonical
Git HEAD at that live test was `26433862b55c157a5f335d674076f86dd40d6b6e`, with
the implementation saved locally and uncommitted. The final release's
`BUILD_INFO.txt` records its committed source snapshot. Historical releases
were not changed.

## Remaining release gates and limits

- The existing `NullGate · PiXi` shortcut still opens the legacy theme
  launcher. Use the new `NullGate · PiXi Root Switch` shortcut.
- The immutable 0.2.0 release bundles document the former workflow. The
  final 0.3.0 package includes a committed source archive and verified runtime
  artifacts. The earlier operations-only staging is historical evidence.
- Reboot/bootstrap is host-assisted because PiXi has no app-callable `su`.
- Rooted ADB must remain enabled during the root-switch session. `adb unroot`
  killed the idle broker on this ROM. Host-independent daemon lifetime is not
  verified; stop cleanly before turning Rooted debugging off.
- No claim is made about power-loss cleanup, deliberate root persistence,
  arbitrary unmodified clients, other ROMs/devices or an upstream maintainer response.
- This check demonstrates persistence after OFF, not after reboot or wallpaper
  changes. Android/ColorBlendr may subsequently update dynamic theme state.
- The side-by-side debug controller remains installed only for development
  checks; the production controller is non-debuggable.
