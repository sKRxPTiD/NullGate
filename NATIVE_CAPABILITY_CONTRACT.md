# NullGate native capability contract v1

## Product boundary

NullGate core is self-contained. Client applications never receive a root shell,
filesystem path, command string, URI, arbitrary JSON, or general Binder proxy.
They request a named capability with a closed parameter type. The root broker
independently authenticates the NullGate controller, checks policy, executes one
registered adapter, and owns rollback until it can prove cleanup.

Shizuku, Magisk and an app-callable `su` are not dependencies. Compatibility
bridges may exist separately, but they are not registered by the normal broker.

## Protocol v2

Every ISSUE frame contains the existing lease identity, target, monotonic time
window and named capability plus one bounded payload discriminator. Version 2
currently permits only:

- `NONE`, for capabilities with no parameters;
- `SYSTEM_THEME_SEED`, containing one opaque ARGB color and one enumerated theme
  style.

The payload cannot be retargeted to a different capability. Unknown kinds,
styles, mismatched targets and non-opaque colors fail closed while parsing.

## First native adapter

`SYSTEM_THEME_SEED_APPLY` targets Android itself. Reads use Android's settings
provider. Writes invoke only `/system/bin/settings` with a fixed operation,
namespace and key; the broker-generated value is one argument. There is no
caller-controlled command, executable, key, namespace, pipeline or shell text.

Before applying a seed, the adapter snapshots the exact existing
`theme_customization_overlay_packages` value. It changes only the color source,
theme style, system palette and application timestamp, preserving unrelated
keys. Revoke, expiry, broker shutdown or failed activation restores the exact
snapshot and verifies equality. A failed restoration remains owned and blocks
new leases.

The candidate is available only when the broker is launched with the explicit
`SYSTEM_THEME_V1` mode. The existing marker launcher omits that mode, so its
security boundary has not silently expanded.

## ColorBlendr relationship

This capability proves the ColorBlendr-shaped use case without requiring or
impersonating ColorBlendr. A future upstream integration can request the same
typed operation after its own identity and user-consent contract is defined.
Unmodified ColorBlendr remains unsupported rather than receiving a fake grant.
