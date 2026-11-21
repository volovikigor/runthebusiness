# Café starter menu (Room v7)

Room v7 adds persistent `cafe_products` and `cafe_recipe_items`, linked to shared business locations and inventory items. Existing save data is retained by an additive migration. On Café screen, tap **Create starter menu** once to seed Espresso (€2.20), Cappuccino (€3.50), and Croissant (€2.90). Each has a persisted recipe and preparation time. Tap **Sell 1** to execute the existing double-entry sale transaction, reducing stock and posting sales/VAT and COGS; results appear on the dashboard and in the journal. The seed is idempotent.

**Important:** recipes are currently denominated in fractional units of the **existing placeholder opening inventory**. They are not real coffee/milk/flour recipes. Consequently COGS are provisional, and inventory is not yet split into ingredients. The next vertical slice should introduce actual stock units and batches with a value-preserving migration from the placeholder opening inventory, then replace these recipes. The simulation VAT rate is still hard-coded at 10%, and there is no customer arrival scheduler.

**Test without clearing data:** upgrade v6→v7, start café if necessary, seed menu, verify three rows and three recipe rows, tap Sell 1 for Espresso, check a new order, revenue/IVA/COGS journal entries and decreased stock, restart and verify all persist. Re-seeding must not duplicate menu.
