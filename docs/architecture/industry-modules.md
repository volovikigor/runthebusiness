# Industry boundaries (transition plan)

## Core versus industry

- `core/business`: cross-industry business settings and location identity.
- `industries/IndustryModule`: industry initialization contract.
- `industries/cafe`: café-specific configuration, creation use case, and location projection.
- `engine/accounting`: shared double-entry accounting.
- `data/local`: Room persistence, migrations and transactional storage.

## Current compatibility boundary

`InitialGameFactory`, `StartingConfiguration`, `CafeLocation` and `CafeLocationEntity` remain in their original packages for binary/source compatibility with existing repositories and Room v3 saves. The new `CafeIndustryModule` delegates to the existing factory via an explicit adapter; this is an intermediate step, not a claim that the legacy factory is already generic.

The shared `BusinessLocation` is a domain projection, **not yet a new Room table**. Do not duplicate mutable location data in two tables without a single transactional write path. A future schema migration should normalize shared location fields only after the repository uses the new contract for reads and writes.

`Employee`, `Equipment`, `InventoryItem`, `Loan`, and `JournalEntry` remain shared. Café-specific concepts such as seating and service must not leak into the shared financial engine.

## Next safe steps

1. Move café construction logic out of `InitialGameFactory` and make the old API a thin compatibility facade.
2. Introduce a normalized shared `locations` table and migrate existing `cafe_locations` without losing foreign-key relationships.
3. Change `GameRepository` to persist industry-neutral and café-specific records in one transaction.
4. Add another industry only when café flows validate the shared abstraction.

The app currently stores only opening objects; runtime game-state mutation persistence is not complete.
