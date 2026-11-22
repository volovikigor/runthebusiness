# Why arrivals may not create sales

PR16 only processes generated arrivals; it does not sell when no arrival is scheduled. Arrival generation only happens during the configured café opening hours. Service requires a menu product, usable recipe, sufficient stock and enough free seats. Each processed arrival becomes SERVED, NO_SEATS or NO_STOCK. PR16 UI showed only 20 latest arrival records and did not show global outcomes, making failures hard to diagnose.

This patch adds a read-only Room-backed simulation diagnostic section on the Café screen. It shows **all-time** arrival counts by status, number of menu products, ready-to-sell products, and exact stock/recipe issues. It provides starter menu and real-ingredient setup buttons when missing. No schema changes and no changes to accounting calculations.

**Manual test:** existing save survives; jump during open hours; diagnostics counts change; with no menu create it; with missing real ingredients install them; observe Ready to sell >0; subsequent arrival generates a sale, with matching Finance journal and inventory movement. If time is after closing, advance to next day's opening. The dashboard sales metric is cumulative, not daily. Android build was not executed remotely.
