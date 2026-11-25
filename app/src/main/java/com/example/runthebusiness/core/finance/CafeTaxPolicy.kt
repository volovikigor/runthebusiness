package com.example.runthebusiness.core.finance

/** Simplified game rules for the pilot economy. */
internal object CafeTaxPolicy {
    const val taxPeriodDays = 30
    const val hospitalityVatPercent = 10

    fun employeeSocialCents(grossCents: Long): Long = percent(grossCents, 6)
    fun employerSocialCents(grossCents: Long): Long = percent(grossCents, 30)
    fun corporateTaxCents(profitCents: Long): Long = percent(profitCents.coerceAtLeast(0), 25)

    private fun percent(cents: Long, rate: Int): Long = (cents * rate + 50) / 100
}
