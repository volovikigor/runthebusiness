# PR15: persisted deterministic arrivals

Advancing the clock (+15 minutes or +1 hour) now plans deterministic guest arrival events in 15-minute slots between the café opening and closing hours, and writes them to Room `cafe_arrivals` in the same transaction as the updated game clock. Unique event IDs combine session, location, day, hour and slot; `INSERT OR IGNORE` prevents duplicate arrivals. No events are created outside opening hours, for a closed café, or with zero seats. Model is intentionally deterministic and currently bounded to one day per advance. Tests cover split-step determinism, closed hours, zero capacity, and step limit.

**Not implemented:** seating, actual concurrent occupancy, customer orders, automatic sales, employee throughput, service times, demand forecasting, daily financial costs, automatic real-time clock. Arrival count is not occupancy. The user interface explicitly labels events as arrivals, not orders. This is the first small slice of the game-day engine.

**On-device checks:** upgrade Room v8→v9 without deleting data; tap +15 minutes repeatedly during open hours, verify arrival count and records; advance outside open hours and verify no new events; restart and confirm arrival records persist; verify previous sales/journal still exist; Dev tab shows `cafe_arrivals`. Android build and migration tests not executed remotely.
