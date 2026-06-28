package com.example.calmsource.tv.ui

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class TvProfileSelectionVerificationTest {

    @Test
    fun `verify profile selection viewmodel flow`() {
        val source = readSourceFile("ProfileSelectionViewModel.kt")
        
        // Verify loading flow is set up correctly from profileRepository
        assertTrue(
            "ViewModel should observe profiles flow from repository",
            source.contains("profileRepository.observeProfiles()")
        )
        assertTrue(
            "ViewModel should convert profiles to StateFlow via stateIn",
            source.contains(".stateIn(")
        )
        
        // Verify selectProfile triggers sessionManager.selectProfile and onComplete
        assertTrue(
            "ViewModel selectProfile should launch inside viewModelScope",
            source.contains("fun selectProfile(") && source.contains("viewModelScope.launch")
        )
        assertTrue(
            "ViewModel selectProfile should invoke sessionManager.selectProfile",
            source.contains("sessionManager.selectProfile(profileId)")
        )
        assertTrue(
            "ViewModel selectProfile should execute onComplete callback",
            source.contains("onComplete()")
        )

        // Verify addProfile inserts profile and executes onComplete
        assertTrue(
            "ViewModel addProfile should check for blank/empty name",
            source.contains("if (name.isBlank()) return")
        )
        assertTrue(
            "ViewModel addProfile should insert profile via repository",
            source.contains("profileRepository.insertProfile(newProfile)")
        )
    }

    @Test
    fun `verify cinematic focus animations scale and border`() {
        val source = readSourceFile("TvProfileSelectionScreen.kt")
        
        // 1. Zoom animation mapping (1.15f scale)
        assertTrue(
            "TvProfileAvatarCard should animate scale factor to 1.15f when focused",
            source.contains("if (isFocused) 1.15f else 1.0f")
        )
        assertTrue(
            "TvProfileAvatarCard should map scaleFactor to graphicsLayer scaleX and scaleY",
            source.contains("scaleX = scaleFactor") && source.contains("scaleY = scaleFactor")
        )

        // 2. Glowing white border mapping (3.dp white border)
        assertTrue(
            "TvProfileAvatarCard should draw a 3.dp border when focused",
            source.contains("width = if (isFocused) LumenExtendedColors.focusRingWidth else 0.dp")
        )
        assertTrue(
            "TvProfileAvatarCard border color should be Color.White when focused",
            source.contains("color = if (isFocused) Color.White else Color.Transparent")
        )
        assertTrue(
            "TvProfileAvatarCard border shape should be CircleShape",
            source.contains("shape = CircleShape")
        )

        // 3. TvAddProfileCard zoom & border
        assertTrue(
            "TvAddProfileCard should animate scale factor to 1.15f when focused",
            source.contains("if (isFocused) 1.15f else 1.0f")
        )
        assertTrue(
            "TvAddProfileCard border width should be 3.dp when focused",
            source.contains("width = if (isFocused) LumenExtendedColors.focusRingWidth else 0.dp")
        )
        assertTrue(
            "TvAddProfileCard border color should be Color.White when focused",
            source.contains("color = if (isFocused) Color.White else Color.Transparent")
        )
    }

    @Test
    fun `verify profile click callbacks trigger session logic and navigation`() {
        val source = readSourceFile("TvProfileSelectionScreen.kt")

        // 1. Profile avatar click
        assertTrue(
            "Click on TvProfileAvatarCard must trigger selectProfile and onProfileSelected",
            source.contains("viewModel.selectProfile(profile.id, onProfileSelected)")
        )

        // 2. Add profile click and dialog confirm
        assertTrue(
            "Click on TvAddProfileCard must show dialog",
            source.contains("onClick = { showAddDialog = true }")
        )
        assertTrue(
            "Dialog creation confirm must call viewModel.addProfile",
            source.contains("viewModel.addProfile(name) {") && source.contains("showAddDialog = false")
        )
    }

    @Test
    fun `verify dialog has no focus trap`() {
        val source = readSourceFile("TvProfileSelectionScreen.kt")
        
        // Search for focus trapping mechanisms inside TvProfileCreationDialog:
        // E.g., focusProperties, exit = { FocusRequester.Cancel }, or intercepting all key events to prevent escape.
        val dialogStart = source.indexOf("fun TvProfileCreationDialog")
        val dialogSection = source.substring(dialogStart)
        
        val hasFocusPropertiesTrap = dialogSection.contains("focusProperties") && dialogSection.contains("FocusRequester.Cancel")
        val hasKeyEventTrap = dialogSection.contains("onPreviewKeyEvent") && dialogSection.contains("KeyEventType") && dialogSection.contains("escape")
        
        // Assert that no focus trap exists in the dialog Box/Column modifiers
        assertFalse(
            "Vulnerability: Dialog has no focusProperties trap to prevent focus escaping to background items",
            hasFocusPropertiesTrap
        )
        assertFalse(
            "Vulnerability: Dialog has no custom key event trap on the outer container to consume directional focus exit",
            hasKeyEventTrap
        )
    }

    private fun readSourceFile(fileName: String): String {
        val basePaths = listOf(
            "app-tv/src/main/java/com/example/calmsource/tv/ui/",
            "../app-tv/src/main/java/com/example/calmsource/tv/ui/"
        )
        for (base in basePaths) {
            val file = File(base + fileName)
            if (file.exists()) return file.readText()
        }
        fail("Could not find source file: $fileName")
        return ""
    }
}
