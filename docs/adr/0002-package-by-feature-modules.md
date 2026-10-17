# ADR 0002: Package by feature (modules)

Status: Accepted

## Context

The project starts as a modular monolith. Module boundaries should be enforced by the compiler where possible, and modules should be easy to extract later.

## Decision

Code is organized per module (`user`, `event`, `reservation`, `payment`, `audit`), not per technical layer. Inside a module, classes are `package-private` unless they are part of the module's deliberate public API. Modules do not use each other's entities or repositories. They talk through a public service or interface, and reference each other's data by ID only. Liquibase changelogs follow the same structure (one folder per module, listed in dependency order in the master changelog).

## Alternatives

- **Package by layer** (`controller`, `service`, `repository`): familiar, but every class must be `public`, so nothing protects module boundaries, and a single feature is spread over many packages.
- **Hybrid with layered sub-packages**: keeps feature grouping, but sub-packages force `public` visibility and weaken the boundary.

## Consequences

- A feature lives in one place and module boundaries are visible in code.
- Cyclic dependencies between modules must be watched. An ArchUnit test or Spring Modulith can be added when the need appears.
- Cross-module foreign keys in the database remain possible. They are an accepted trade-off while this is a monolith.
