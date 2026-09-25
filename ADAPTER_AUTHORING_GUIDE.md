# NullGate adapter authoring guide

NullGate adapters are narrow capability translators. An adapter may translate
one approved, typed effect into a device operation, but it must never become a
general shell, arbitrary settings writer, or replacement for caller
authentication.

## Before writing an adapter

1. Name the capability and its smallest typed payload.
2. Define the exact target state and the exact prior state that must be restored.
3. Define the maximum lease duration and cleanup for expiry, revoke, crash,
   timeout, and partial activation.
4. Identify the package, version, and signer policy for each client.
5. Add a threat-model entry and a host-only policy test before device work.

## Adapter boundary

The controller owns caller identity, user confirmation, lease state, expiry,
revoke, and audit events. The adapter owns only the typed device operation and
its verification. The root-side broker receives the controller's authenticated
request; the client never receives root, a shell, a file descriptor, or a
broker transport handle.

An adapter must fail closed when it cannot prove activation or restoration. A
lost reply is unknown—not success, denial, or permission to continue. Recovery
must be idempotent and preserve an unresolved record until cleanup is
confirmed.

## Review checklist

- [ ] Capability has an explicit version and exact field schema.
- [ ] Unknown fields and enum values are rejected.
- [ ] Target package and signer policy are separate from adapter logic.
- [ ] The package-capability adapter registration is unique and cannot be shadowed.
- [ ] Lease duration has a hard upper bound.
- [ ] Activation is verified after the write.
- [ ] The exact prior state is recorded before mutation.
- [ ] Expiry and revoke restore the recorded state.
- [ ] Cleanup failure blocks new admissions.
- [ ] Restart and process-death paths reconcile instead of inferring success.
- [ ] Spoof, replay, malformed-payload, lifecycle, and cleanup tests exist.
- [ ] A supervised device test has explicit stop conditions and rollback.

## Promotion path

Build and test a capability with the first-party test client first. Add a
private client fork second. Only then should an upstream integration be
proposed. A peer product later can reuse the broker-neutral contract while
keeping device-specific adapters and signer policy in reviewed modules.
