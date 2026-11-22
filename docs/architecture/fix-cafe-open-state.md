# Root cause: zero arrivals despite menu and stock

The default `CafeLocation.isOpen` is false. `CafeStartingFactory` did not set it to true, so all newly initialized cafés were persisted as closed. `GameRepository.advanceGameTime` explicitly skips arrival generation when `shared.isOpen` is false. Thus no arrivals, orders or revenue regardless of stock/menu readiness.

Fix: explicitly initialize new cafés with `isOpen=true`, expose the persisted open/closed state and business hours in the home UI, and provide a manual Open café / Close café action for existing saves. **No migration, no data deletion and no silent alteration of previously saved café state.** Existing games will show CLOSED until the player taps Open café. Arrivals only occur within 08:00–17:00 in the default configuration.

Test: existing game opens and shows CLOSED; tap Open café; advance clock during opening hours; arrival counters should increase and sales may be generated if stock and menu are ready. Advance after 17:00 to confirm no arrivals; closing café manually suppresses arrivals; restart and verify open state persists. The Menu tab's advance-hour control also generates arrivals but does not process pending sales until the Café tab is opened. Android build not run remotely.
