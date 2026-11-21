# First on-device sale (diagnostic vertical slice)

This feature intentionally exposes a **diagnostic** button on the database inspector, not a finished café point-of-sale screen.

After **Seed opening entries**, tap **Sell diagnostic item (€50)**. The temporary product consumes 0.01 units of the single placeholder opening inventory batch (initial value €3,000 per unit), creating an illustrative €30 COGS. Gross €50 with a simulated 10% VAT produces net €45.45 and output VAT €4.55.

All changes are committed in one Room transaction: optimistic inventory batch update, business events, two journal entries (sale and COGS), and a customer order summary. Room rolls back all changes on failure. The journal and customer_orders table are visible in the inspector after refresh and persist across app restarts.

**Limitations to address before real gameplay:**
- Product and recipe are diagnostic and created in memory; no product catalog or persisted order lines yet.
- Opening inventory is a single placeholder asset; its partial consumption is only a demonstration.
- VAT 10% is an explicit game assumption, not a legal-rate lookup.
- No payment reconciliation, daily aggregation, customer arrival scheduler, real product inventory or purchase flow.
- The sale button currently uses English diagnostic labels.

Do not interpret this as an operational café simulation yet.
