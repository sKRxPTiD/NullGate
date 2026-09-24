# Security policy

NullGate is an experimental privilege broker. It is not production-ready, and
its Android transport, SELinux behavior, lifecycle handling, and privileged
deployment still require device-level review.

Do not publish a suspected vulnerability before giving the project owner a
reasonable opportunity to investigate it. Until a private reporting address is
published, open a GitHub issue containing no exploit details and ask for a
private contact channel.

Never attach signing keys, device identifiers, broker logs, account tokens, or
other secrets to a public issue.

The current boundary and known limitations are documented in
`THREAT_MODEL.md` and `SECURITY_AUDIT.md`.
