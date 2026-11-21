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

## Room v4: shared location bridge

Room v4 introduces `locations` (id, businessId, name, opening/closing hours, monthly rent, isOpen) with a foreign key to `businesses`. Migration 3→4 copies **existing values** from `cafe_locations`; it does not reconstruct them from defaults.

New games insert both `locations` and `cafe_locations` in the same Room transaction. `GameRepository.loadBusinessLocations` reads the cross-industry table. `GameRepository.updateSharedLocation` updates both representations atomically, preserving compatibility with existing `loadOperations` and foreign keys.

**Deliberate limitation:** `cafe_locations` still contains duplicate shared columns, and employee/equipment/inventory foreign keys still target `cafe_locations`. Do not write directly to either table outside the repository; doing so can cause divergence. Full normalization requires rebuilding the café extension and dependent foreign keys in a subsequent migration. This is a safe compatibility bridge, not the final relational design.

To verify on device: upgrade an existing v3 install (do not clear app data), inspect `locations` and `cafe_locations`, confirm identical shared values, and confirm all existing employees, equipment, inventory and journal entries remain. Re-run Seed opening entries and verify no duplicates.

## Room v5: completed location normalization

Migration 4→5 backs up café and operational records into temporary tables, rebuilds `cafe_locations` as a café-only extension keyed to `locations.id`, and changes employee, equipment, inventory item and inventory batch location foreign keys to `locations.id`. Existing IDs and values are copied back. The migration is intended to run atomically under Room's migration transaction. Shared name, rent, hours and open status now exist only in `locations`.

`GameRepository.loadOperations` joins the shared identity with café-specific fields in memory. `GameRepository.updateSharedLocation` updates only `locations`; no duplicate write is needed. The domain model `CafeLocation` remains a combined read model for existing simulation code.

A deterministic `GameClock` and `GameRepository.advanceGameTime` persist game time, but **no event scheduler, daily sales or payroll posting is implemented yet**.

**Stacked PR warning:** PR #6 is based on PR #5's branch. Merge #5 first, then retarget #6 to master (or allow GitHub to update the base automatically). Do not merge #6 directly to master before #5.

**Test:** install over existing Room v3/v4 data without clearing storage. Verify migration completes, employees/equipment/inventory and journal survive, shared location fields remain intact, seed is idempotent, and `GameClockTest` passes. SQLite foreign-key integrity and Room schema validation should be checked on device.
