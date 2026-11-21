# PR14: first game shell

The app now launches to a dedicated Café home screen, with four destinations:

1. **Café**: branded warm-toned café panel, persisted game day/time, manual +15min/+1h clock controls, aggregate sales and gross profit, and six most recent persisted orders.
2. **Menu**: the existing product sales and inventory setup UI, unchanged.
3. **Finance**: a player-facing journal ordered newest-first, filterable by day, expandable to debit/credit lines. The journal still includes opening entries and all accounting events.
4. **Dev**: the original Room inspector, retained for troubleshooting.

This change uses the existing Room v8 data and requires **no database migration**. It does not invent occupancy, visitor arrivals, live orders or P&L figures. The stylized café panel is an initial Compose visual shell, not yet a rendered interactive café interior. Clock advancement persists but **does not** generate customer orders or recurring costs. Metrics are cumulative and do not yet represent period financial statements.

**Manual verification**: upgrade without clearing data; home screen displays existing café, clock and cumulative sales; +15min/+1h persists after restart; Menu still sells products and creates journal lines; returning to Café refreshes on re-entry; Finance newest entries appear first, day filter works, tapping expands lines; Dev retains inspector. Build and emulator testing required before merge.
