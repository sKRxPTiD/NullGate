# Supervised interrupted-theme recovery rehearsal

Status: complete. Interrupted-theme recovery, controller/client reconciliation
and the corrected foreground countdown/expiry workflow passed. The signed fix
passed all 199 host checks, nineteen launcher regressions and the simulated
helper suite. No further crash or lease test is required for this acceptance.

Wolf authorized this rehearsal and enabled Rooted debugging. PiXi serial
`54110DLAQ0043W` passed the platform/signer preflight with root ADB,
`u:r:su:s0`, and SELinux Enforcing. The managed runtime was absent and the
controller preference file held only approval generation 9, with no lease.

Exact independently captured pre-test secure theme value:

```json
{"_applied_timestamp":1790127128537,"android.theme.customization.color_both":"1","android.theme.customization.system_palette":"1963A7","android.theme.customization.color_source":"home_wallpaper","android.theme.customization.color_index":"2","android.theme.customization.theme_style":"VIBRANT"}
```

The real system-theme adapter writes `theme.snapshot`; it does not create an
ephemeral `.lease` marker. The mixed marker/snapshot tests added in `eb61f04`
are synthetic coverage, not proof of a normal theme-lease defect. This live
test must validate the actual theme snapshot path, not manufacture a marker.

## Live evidence

Wolf reported GRANTED for Ember / Expressive. The live setting was preset
palette `9b3d20`, style `EXPRESSIVE`; lease
`a99b34eb-675e-42d0-8c6e-04ffae964b87` had elapsed deadline `407993316`.
The broker log independently records the lease's GRANTED decision. Broker PID
`27215` was rechecked against its root UID and exact pinned `SYSTEM_THEME_V1`
command line before deliberate SIGKILL. Its root-owned, mode-600, 299-byte
snapshot contained the independently saved baseline. No marker was created.

The installed launcher's guarded recovery succeeded and returned ADB to UID
2000. Independent comparison found the recorded baseline, archived snapshot
value and restored secure setting identical, including the original timestamp.
The setting's SHA-256 without a terminal newline is
`4de9a2f6befef3db0768795e86eb4183f5dbbb97c176c5c7a4c6514e2e197580`.

Archived snapshot:
`device/logs/theme-recovery-54110DLAQ0043W-20260928T235637Z-xw1nTX.snapshot`
SHA-256: `896b7ccd08b817e47d7f3a585d799e937f92ec3dbdf8351a180b06dd89663b81`.

Archived broker log:
`device/logs/broker-54110DLAQ0043W-20260928T235638Z-eESHtF.log`
SHA-256: `64243a7c09eb1b9e8ddb14f01a37fa635951a71b49a7f2e9a0112dd7ee6e4fbc`.

After expiry, client reconciliation correctly reported unconfirmed cleanup
because the broker was absent. The reviewed controller-record recovery gate
verified the exact restored-theme hash, absent runtime/process, package/version/
signer, expired lease, uncertain phase, file metadata and record hash. It
archived and removed only the reviewed record:
`device/logs/controller-record-54110DLAQ0043W-20260928T235913Z-0ab52cf048ef2209f9e523596a73672f212a0e2787b3fe6eb56b55464040ebc9.xml`.
Its SHA-256 matches the digest in that filename. Subsequent normal client
reconciliation displayed `Reconciled clean: no active theme lease remains.`
Request was enabled; Restore and Reconcile were disabled. No app-data reset or
unreviewed preference deletion was used.

Final doctor passed installed version/signer pins, absent managed runtime and
ordinary ADB. Rooted debugging must now be switched off separately on PiXi;
user confirmation of that toggle is pending.

## Exposed UI defect

The original client refreshed its lease state only during creation/result
handling. Staying on the screen left an old countdown and disabled Reconcile
button after expiry. Restarting the client enabled reconciliation. The source
fix schedules foreground countdown updates, refreshes on resume and stops
callbacks on pause/expiry. It does not infer cleanup from elapsed time or
overwrite terminal/pending/uncertain messages. This changes Theme Client APK
bytes; immutable RC2 remains unchanged. Updated Theme Client APK SHA-256:
`b999f127f10caf934a0be2f8943e62c0faa81d2464f06c7a45c02e6f5e4c91d9`.
Controller and broker hashes remain identical to their earlier live-validated
artifacts. The guarded helper installed only the updated Theme Client after
checking absent broker/runtime and the existing paired controller identity.
It opened a fresh system-theme broker (PID `29975`) and the client with an
empty lease store.

## Updated-client normal-expiry pass

Wolf approved Ember / Expressive lease
`48d0fb5c-21c5-440c-ab0f-13339dd291e8`, issued at elapsed `408290684` with
deadline `408410684`. Controller and broker independently recorded GRANTED.
The countdown replaced the brief grant text and advanced on the open screen
(Wolf observed 76 seconds; the inspected UI later showed 52 seconds).
Without restarting, it reached `Lease deadline passed; reconcile to confirm
restoration.` Request and Restore were disabled; Reconcile was enabled.
The broker logged `cleanup=EXPIRED` and the setting returned to the exact saved
baseline including its original timestamp. Elapsed time alone was not used as
cleanup proof.

Normal reconciliation displayed `Reconciled clean: no active theme lease
remains.` The client lease store was empty, and the controller retained only
approval generation 3. Guarded Stop archived the log and removed the runtime,
returning ADB to UID 2000. Final doctor passed. Archived log:
`device/logs/broker-54110DLAQ0043W-20260929T000534Z-7Qy9fs.log`.
Release bundles, signing files and the protected malformed filename were not
changed. Source and the installed client include the post-RC2 fix; immutable
RC2 does not.
