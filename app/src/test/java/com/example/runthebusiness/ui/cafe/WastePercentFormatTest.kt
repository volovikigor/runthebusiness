package com.example.runthebusiness.ui.cafe

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WastePercentFormatTest {
    @Test fun literalPercentInLabelDoesNotCrashFormatter() {
        val text = "Waste as %% of net sales: %.1f%%".format(Locale.ROOT, 18.4)
        assertEquals("Waste as % of net sales: 18.4%", text)
    }
}
