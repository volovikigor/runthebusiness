# PR20 — unified simulation step (first architectural extraction)

## Call chain

- MainActivity automatic clock: `simulation.advance(15L)`
- Café screen +15m/+1h: `simulation.advance(15L/60L)`
- Menu screen +1h: `simulation.advance(60L)`
- Café screen startup: `simulation.resumePending()`

`GameSimulationEngine.advance` is the single coordinator. It first retries previously pending arrivals, advances time and generates arrivals via `GameRepository.advanceGameTime`, then services arrivals through `CafeServiceRepository.processPending`. Existing `CafeSaleRepository.sellMenuItem` and `CompleteCafeSaleUseCase.execute` remain responsible for stock, FIFO, VAT, COGS and accounting.

A shared Mutex serializes all calls from the activity and screens. The database still uses **separate transactions**: one for clock+arrival generation and one per arrival for sale+status. This is intentionally NOT one global transaction. If service processing fails after the clock commits, pending arrivals remain in SQLite and can be retried on the next step or Café screen opening. Do not claim atomicity of the entire tick.

No database migration and no changes to pricing, demand or seating. Test auto ×20, manual +15m/+1h from both screens, persistence after restart, and no duplicate arrivals/orders. Kotlin/Gradle build has not been run remotely.

## Follow-up

Move simulation lifecycle into a retained application-scoped component and add deterministic end-to-end Room tests for partial failures. Avoid direct calls to GameRepository.advanceGameTime outside the coordinator.
