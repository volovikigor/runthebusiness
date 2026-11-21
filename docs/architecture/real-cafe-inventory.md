# First real café stock and resale flow

After creating the starter menu, press **Set up ingredients and supplier croissants**. In one Room transaction, the app reclassifies €300 of the remaining original placeholder inventory asset (0.10 of the initial €3,000 unit) into:
- 1,000 g coffee beans × €0.04/g = €40
- 10,000 ml milk × €0.01/ml = €100
- 100 finished supplier croissants × €1.60 = €160

No new asset or expense is recognized during reclassification: inventory total value is unchanged. The historical placeholder batch retains the unconverted value. This operation is idempotent. It requires at least 0.10 units of the original batch to remain; if insufficient, the app refuses conversion rather than silently inventing stock.

Existing recipes are replaced: Espresso = 9 g beans (€0.36), Cappuccino = 9 g beans + 150 ml milk (€1.86), Croissant = 1 purchased finished unit (€1.60). The latter is RESALE, not café baking. Selling uses the same transactional order/journal/stock mechanism, now with multiple inventory items and batches. The sales engine uses a synthetic one-item consumption recipe internally for resale; this is *not* a manufacturing recipe stored in Room.

**Limitations:** these are illustrative costs and starter quantities, not researched supplier quotations. No supplier entities, procurement, delivery scheduling, expiration enforcement, wastage, staff time, or batch production yet. The original placeholder inventory remains in the save and is not automatically removed. The game still uses a simulated 10% VAT assumption.

**On-device test:** update existing save without clearing data, open Café, create starter menu if necessary, set up ingredients once, sell one Espresso, Cappuccino and Croissant. Expect three orders, corresponding balanced sales and COGS entries, and independent bean/milk/croissant stock reductions. Re-run setup; it should not create duplicate stock. Restart app and check persisted results. Do not run conversion if the original placeholder batch has been mostly consumed.
