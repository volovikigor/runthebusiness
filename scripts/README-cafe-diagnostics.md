# Café diagnostics from Android to Windows

This is a **development-only** workflow. It requires the installed **debug** APK (Android Studio Run), an unlocked USB-debugging phone, and Android SDK Platform-Tools. A release APK is not accessible with `run-as`.

1. Merge this PR and run the updated app from Android Studio.
2. Connect your phone via USB, accept its debugging prompt, and run `adb devices` to verify exactly one device is connected.
3. Open PowerShell at the repository root and start the watcher **before** simulating:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pull-cafe-report.ps1 -Watch
```

4. On the phone tap **Advance 7 days**. The app updates `files/cafe_diagnostic_latest.csv` in its private storage after a successful weekly jump. The watcher checks every 10 seconds and saves a new timestamped CSV under `reports\` only when the file changes. Stop watching with Ctrl+C.

For a one-off copy after a jump:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\pull-cafe-report.ps1
```

The automatic ADB CSV now contains **only the last 14 completed game days** for transactional data, plus the current catalog, recipes, stock batches and a current-inventory snapshot. It also includes `product_sales_14d`, `product_sales_daily` and `group_size_conversion` to diagnose basket selection and stockouts. The **Finance → Export full audit CSV** action still exports the entire history.\n\nThe CSV is a **multi-section diagnostic export**, not a flat importable table. Each section starts with `"TABLE","<name>"`, then its own header. It includes recent arrivals, orders, lines, current product/recipe setup, inventory and batches, recent stock movements, journal entries/lines, business events, product mix and two derived 14-day summaries (`customer_flow_summary_14d`, `break_even_summary_14d`). All financial amounts in derived summaries are **cents**. The summaries cover the last completed days; transactional raw tables include the same recent 14-day window. Share the CSV for analysis instead of phone screenshots.

No desktop server or Android storage permissions are needed. The PC must be on and connected to the device; the app cannot write directly to an arbitrary Windows directory without a transfer mechanism. For the public release retain the existing **Finance → Export full audit CSV** Android document picker.

Note: The automatic report is intentionally bounded to recent activity; the full-history Finance export may still grow substantially.
