# NullGate universal test matrix

This matrix keeps NullGate client-neutral. ColorBlendr is the first populated
row; future apps must satisfy the same gates without receiving broader
privilege.

| Area | Required evidence | Current status |
| --- | --- | --- |
| Request schema | Exact fields, types, enums, and duration ceiling | Passed for `SYSTEM_THEME_SEED_APPLY` |
| Caller identity | UID, sole owner package, version, and signer | Passed for reviewed ColorBlendr policy |
| Client scope | Registered package is limited to named capabilities | Passed for ColorBlendr and first-party test client |
| Approval | Visible, result-bound, obscured-touch-protected confirmation | Passed in supervised PiXi test |
| Grant receipt | Correlated lease ID and bounded expiry | Passed with first-party test client |
| Result policy | Exact schemas; ambiguous or future decisions remain unknown | Passed through shared protocol policy |
| Immediate revoke | Revoke returns confirmed cleanup | Passed with first-party test client |
| Natural expiry | State restores exactly at lease expiry | Passed with first-party test client |
| Lost result | Unknown state survives and requires reconciliation | Host policy and lifecycle tests passed |
| Process restart | Pending or active records reconcile fail-closed | Host and supervised controller tests passed |
| Adapter failure | Partial activation or cleanup blocks new admissions | Broker regression tests passed |
| Adapter registry | Duplicate package-capability registration is rejected | Broker registry tests passed |
| Concurrent capacity | New grants stop at the active-lease policy ceiling | One-active-lease policy test passed |
| Request rate | New requests stop at the per-client elapsed-time ceiling | Persistent-window policy tests passed |
| External app, private path | Signer-pinned client build on PiXi | Passed with the matched private ColorBlendr fork |
| External app, public path | Official upstream signer and reviewed release | Optional; pending upstream ColorBlendr adoption |

## Adding a new client

For each new client, record the package, version, signer, capability, maximum
lease, exact restoration proof, and supervised-device run. A private matched
build can complete the private row but cannot be presented as an official
upstream release.

## Model and deployment gates

Routine documentation and pure policy tests can use a light model. New broker
identity, lease, revoke, or privileged deployment logic requires the stronger
implementation review. Astra is reserved for the final threat-model and
privileged-deployment audit.
