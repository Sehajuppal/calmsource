package com.example.calmsource.core.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guards Android semantic tokens against tokens/lumen.json codegen drift. */
class LumenTokenParityTest {

    @Test
    fun coreColorsMatchGeneratedJson() {
        assertEquals(LumenTokensGenerated.Color.bgBase, LumenTokens.Color.bg)
        assertEquals(LumenTokensGenerated.Color.surfaceCard, LumenTokens.Color.surface)
        assertEquals(LumenTokensGenerated.Color.brandBase, LumenTokens.Color.brand)
        assertEquals(LumenTokensGenerated.Color.danger, LumenTokens.Color.danger)
    }

    @Test
    fun radiusAndMotionMatchGeneratedJson() {
        assertEquals(LumenTokensGenerated.Radius.lg, LumenTokens.Radius.lg)
        assertEquals(LumenTokensGenerated.Radius.xxl, LumenTokens.Radius.xxl)
        assertEquals(LumenTokensGenerated.Motion.focusMs, LumenTokens.Motion.focusScaleMs)
        assertEquals(LumenTokensGenerated.Motion.springStiffness, LumenTokens.Motion.springStiffness, 0.01f)
    }

    @Test
    fun typeScaleUsesGeneratedValues() {
        val mobile = buildLumenTypeScale(isTv = false)
        val generated = generatedLumenTypeScale(isTv = false)
        assertEquals(generated.display, mobile.display)
        assertEquals(generated.rowTitle, mobile.rowTitle)
        assertEquals(generated.meta, mobile.meta)
    }
}
