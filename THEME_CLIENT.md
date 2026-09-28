# NullGate Theme Client

The NullGate Theme Client is the second first-party external client and the
first operator-facing client built on the typed app-to-controller contract. Its
package is `org.nullprotocol.nullgate.themeclient`, version code 1. It is a
separate Android package and receives its own UID.

## Typed request

The client offers three closed palette seeds and four broker-defined theme
styles. It can request one two-minute `SYSTEM_THEME_SEED_APPLY` lease. It sends
only protocol version, opaque ARGB seed, enumerated style and bounded duration.
It cannot send a command, path, settings key, package target, JSON document or
arbitrary broker payload.

NullGate independently identifies the caller, shows the selected effect for
approval and owns activation and restoration. The client receives only a lease
ID and monotonic expiry receipt. It can request early restoration or reconcile
an uncertain result.

## Identity and lifecycle

The client is admitted only when its package, version, sole-package UID and sole
current signer match the closed controller registry. For the private first-party
build, its signer must match the installed NullGate controller signer.

Before dispatch, the client durably records `PENDING`. A valid grant advances to
`ACTIVE`; malformed, missing or ambiguous results advance to `UNKNOWN`. Another
lease cannot be requested until NullGate confirms revoke or reconciliation.
Expired local time is not treated as cleanup proof.

The client preserves the last submitted palette and style separately from lease
state so Android theme-driven Activity recreation cannot display default
selectors beside a different active request. Once an active local deadline has
passed, the UI explicitly requests reconciliation; elapsed local time still
does not claim that broker restoration succeeded.

## Current gate

The signed host build, client policy tests, lifecycle tests, APK identity,
paired signer and checksum verification pass. The reviewed 0.2.0 candidate also
passed supervised PiXi installation, visible approval, explicit revoke, natural
expiry, exact restoration and reconciliation. Release packaging remains a
separate gate, and the immutable 0.1.0 bundle remains the release baseline.
