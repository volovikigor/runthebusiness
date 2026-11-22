# PR19 — foreground automatic clock

The activity owns one coroutine for the entire tab shell. Clock speed is PAUSE, x1, x5 or x20. A tick advances 15 **game** minutes, then processes all pending arrivals through the existing seating/sales/accounting pipeline. Intervals: x1 = 15 real seconds, x5 = 3 seconds, x20 = 750 ms. The simulation keeps running while the user switches between Café, Menu, Finance and Dev tabs; the Café dashboard refreshes when returning and after ticks. Game time, arrivals, sales, inventory and journal are persisted in Room.

The activity cancels the tick coroutine when the app is backgrounded; speed resets to PAUSE, so reopening never silently catches up. A process restart also begins paused. A failed tick stops the clock and shows an error on Café. Manual time jump buttons remain available. No schema migration.

This is a first **foreground** clock, not an Android background service. Ticks process 15-minute simulation batches rather than continuous minute-by-minute arrivals. Speed control is a temporary gameplay control, not yet part of saved settings. Do not assume game time progresses when the app is closed.

Manual test: open Café, choose x20, observe clock/arrivals/orders/sales change; switch to Finance for 5 seconds, return and verify progress; pause and verify no changes; background the app, reopen and verify speed is paused and no offline progress; restart and verify persisted data remains. Gradle build not run remotely.
