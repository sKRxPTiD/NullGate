# ColorBlendr upstream submission checklist

- [x] Based on current upstream `master` commit `5b078e92`.
- [x] Patch applies cleanly to a fresh checkout.
- [x] Kotlin compilation passes.
- [x] Debug APK assembly passes.
- [x] Result-policy unit tests pass.
- [x] Lost, malformed, or unconfirmed results remain uncertain until the
      controller confirms reconciliation.
- [x] NullGate-specific lint errors resolved.
- [x] Existing ColorBlendr modes remain unchanged.
- [x] No general shell or arbitrary command interface added.
- [x] PiXi's installed ColorBlendr, app data, Shizuku, and root state untouched.
- [x] Choose the GitHub account/fork that will own the contribution.
- [x] Confirm the public location and build instructions for NullGate that
      maintainers can review.
- [ ] Obtain maintainer agreement on the version-policy coordination.
- [ ] Run device instrumentation with an officially signed candidate build.
- [ ] Review and admit the official release version in NullGate.
- [ ] Perform the privileged PiXi deployment audit before live testing.
