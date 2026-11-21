# First café sales domain operation

`CompleteCafeSaleUseCase.execute(state, order, vatRatePercent)` is pure in-memory domain logic. It validates product prices and availability, checks ingredient stock, consumes batches FIFO, creates sales/VAT and COGS journal entries through the shared AccountingEngine, and marks the order completed. The input state is not mutated.

**Not yet a complete playable sale:** customer orders, stock changes, business events, and journal entries from this use case are NOT yet saved to Room in a single transaction. Do not wire this directly to a production button before adding persistence, event idempotency and rollback. A later PR should add order/line entities and a transactional repository operation.

The VAT percentage is an explicit simulation input (default 10%) rather than a verified current legal tax rule. A future tax engine must select a rate from product tax category, location, date and profile; mixed VAT baskets need per-line calculation and rounding. Likewise, per-batch fractional inventory valuation currently rounds at the batch level, which requires reconciliation policy for complex recipes.

Current tests cover basic VAT, COGS, FIFO stock decrement, oversell and duplicate protection.
