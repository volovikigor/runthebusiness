# Industry boundaries (transition plan)

## Core versus industry

- `core/business`: cross-industry business settings and location identity.
- `industries/IndustryModule`: industry initialization contract.
- `industries/cafe`: café-specific configuration, creation use case, and location projection.
- `engine/accounting`: shared double-entry accounting.
- `data/local`: Room persistence, migrations and transactional storage.

## Current compatibility boundary

`InitialGameFactory` remains as a thin compatibility facade; actual opening café construction now lives in `industries/cafe/CafeStartingFactory`. `GameRepository` calls `CreateCafeUseCase`. The legacy `StartingConfiguration`, `CafeLocation` and `CafeLocationEntity` remain in their original packages to preserve existing callers and Room v3 saves. `CafeStartingConfiguration` maps to the legacy configuration via an explicit transitional adapter.

The shared `BusinessLocation` is a domain projection, **not yet a new Room table**. Do not duplicate mutable location data in two tables without a single transactional write path. A future schema migration should normalize shared location fields only after the repository uses the new contract for reads and writes.

`Employee`, `Equipment`, `InventoryItem`, `Loan`, and `JournalEntry` remain shared. Café-specific concepts such as seating and service must not leak into the shared financial engine.

## Next safe steps

1. Completed: move café construction logic out of `InitialGameFactory` and make the old API a thin compatibility facade.
2. Introduce a normalized shared `locations` table and migrate existing `cafe_locations` without losing foreign-key relationships.
3. Change `GameRepository` to persist industry-neutral and café-specific records in one transaction.
4. Add another industry only when café flows validate the shared abstraction.

The app currently stores only opening objects; runtime game-state mutation persistence is not complete.
