package com.example.calmsource.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XtreamHttpWarningRegressionTest {

  @Test
  fun `connectAndSyncXtream uses proceedDespiteHttpWarning instead of showHttpWarning flag`() {
    val source = File("src/main/java/com/example/calmsource/ui/SettingsScreens.kt").readText()
    assertTrue(source.contains("fun connectAndSyncXtream(proceedDespiteHttpWarning: Boolean = false)"))
    assertTrue(source.contains("connectAndSyncXtream(proceedDespiteHttpWarning = true)"))
    assertFalse(
      "Proceed Anyway must not clear showHttpWarning before calling connect without acknowledgement",
      source.contains("showHttpWarning = false\n                        connectAndSyncXtream()\n")
    )
  }
}
