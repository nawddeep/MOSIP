# tools/derby

Standalone adapter test, **not the client schema**. It runs the neutral liveness adapters against a hand-made Apache Derby database. The real integration (client DDL, real `AuditManagerService`) is in `desktop/`; see `docs/liveness/10-desktop-derby-integration.md`.
