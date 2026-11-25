# Café diagnostics from Android to Windows

Install the debug APK from Android Studio. Connect a USB-debugging phone and make sure it appears in `adb devices`. The watcher uses `run-as`, which requires a debuggable app.

1. Start the watcher from the repository root in PowerShell.

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\pull-cafe-report.ps1 -Watch
   ```

2. To measure the first month, start a new café. Record any changed settings. For the automated baseline, enable auto-order for core ingredients, automatic payments for all scheduled bills, and automatic cash deposits.
3. On the phone, choose **Skip → Run 30 days (diagnostic)**. This closes 30 days and stops before the next shift. A run from Day 1 at 08:00 reports Days 1–30. **Next week** still exports up to 14 completed days.
4. Take the new timestamped CSV from `reports\`. To copy it once without the watcher, run the same command without `-Watch`.

The file contains separate tables marked by `TABLE`. `daily_economics` reports posted profit, tax, interest, principal movements, cash and bank separately, and accumulated account balances. `loan_schedule_projected` is a contract projection. Use it separately from the posted numbers, as repayments are not posted by the current simulation. `stock_waste` reports quantities and value by item and reason for the selected window. The `*_14d` summary tables retain their 14-day lookback. Financial values are in cents.
