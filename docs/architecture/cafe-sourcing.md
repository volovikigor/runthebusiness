# Café sourcing and preparation

Do not assume every menu item is manufactured in the café. Sourcing is independent of service/preparation time.

- `RESALE`: purchased finished item, e.g. croissant, cheesecake, bottled water. Sale consumes **one finished-stock unit** at its batch cost. Warming or plating takes staff time but does not create a recipe transformation.
- `PREPARED`: made to order from ingredients, e.g. espresso, cappuccino, sandwich. Sale consumes ingredients according to a recipe, FIFO batches, and posts COGS.
- `BATCH_PRODUCED`: produced ahead of sale. Manufacturing consumes ingredients into finished-goods inventory at production time; sale consumes finished units. This is a future capability, not implemented.

Room v8 adds `fulfillment` and optional `finishedStockItemId` to `cafe_products`, with a backward-compatible default of `PREPARED`. This PR deliberately **does not reclassify existing Croissant** because its v7 recipe still points at placeholder opening inventory. Silently changing the classification without implementing finished-stock consumption would produce wrong COGS. The next PR must create actual inventory items and batches for beans, milk and purchased croissants, reconcile the opening stock value, replace legacy placeholder recipes, then switch croissant to RESALE and update the sale use case accordingly.

Current UI and sales are unchanged; this PR is a narrow migration and invariant foundation. Check upgrade v7→v8 without clearing data and run CafeFulfillmentRulesTest.
