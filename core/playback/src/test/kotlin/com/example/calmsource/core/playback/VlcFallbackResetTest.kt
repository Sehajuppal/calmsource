package com.example.calmsource.core.playback

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.mockito.MockedStatic
import org.junit.After

class VlcFallbackResetTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockPackageInfo: PackageInfo
    private lateinit var mockedLog: MockedStatic<android.util.Log>

    @Before
    fun setUp() {
        mockedLog = org.mockito.Mockito.mockStatic(android.util.Log::class.java)
        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)
        mockPackageManager = mock(PackageManager::class.java)
        mockPackageInfo = mock(PackageInfo::class.java)

        // Mock Context
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.packageName).thenReturn("com.example.calmsource")
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.getSharedPreferences("vlc_fallback_state", Context.MODE_PRIVATE)).thenReturn(mockPrefs)

        // Mock SharedPreferences Editor
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)

        // Set version code fields on PackageInfo mock
        mockPackageInfo.versionCode = 1
        mockPackageInfo.longVersionCode = 1L

        // Stub PackageManager
        try {
            whenever(mockPackageManager.getPackageInfo(eq("com.example.calmsource"), anyInt())).thenReturn(mockPackageInfo)
        } catch (_: Exception) {}

        try {
            whenever(mockPackageManager.getPackageInfo(eq("com.example.calmsource"), any<PackageManager.PackageInfoFlags>())).thenReturn(mockPackageInfo)
        } catch (_: Exception) {}

        // Reset VlcPlayerBackend static initFailed field using reflection
        try {
            val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
            initFailedField.isAccessible = true
            initFailedField.set(null, false)
        } catch (_: Exception) {}
    }

    @After
    fun tearDown() {
        if (::mockedLog.isInitialized) {
            mockedLog.close()
        }
    }

    @Test
    fun testAppVersionUpdateTriggersReset() {
        // Stored version: 1L, stored status: init failed is true
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Current version is updated to 2L
        mockPackageInfo.versionCode = 2
        mockPackageInfo.longVersionCode = 2L

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences resets init_failed, version_code, and last_failure_timestamp
        verify(mockEditor).putBoolean("init_failed", false)
        verify(mockEditor).putLong("version_code", 2L)
        verify(mockEditor).putLong("last_failure_timestamp", 0L)
        verify(mockEditor).apply()
    }

    @Test
    fun testVersionUnchangedButFirstTimeTriggersReset() {
        // No stored version (defaults to -1L)
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(-1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Current version is 1L
        mockPackageInfo.versionCode = 1
        mockPackageInfo.longVersionCode = 1L

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences resets init_failed and saves version
        verify(mockEditor).putBoolean("init_failed", false)
        verify(mockEditor).putLong("version_code", 1L)
        verify(mockEditor).putLong("last_failure_timestamp", 0L)
        verify(mockEditor).apply()
    }

    @Test
    fun testTimeElapsedOver24HoursTriggersReset() {
        // Version unchanged
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Failure timestamp is older than 24 hours (25 hours ago)
        val failureTime = System.currentTimeMillis() - (25 * 60 * 60 * 1000)
        whenever(mockPrefs.getLong(eq("last_failure_timestamp"), anyLong())).thenReturn(failureTime)

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences resets init_failed and last_failure_timestamp
        verify(mockEditor).putBoolean("init_failed", false)
        verify(mockEditor).putLong("last_failure_timestamp", 0L)
        verify(mockEditor).apply()
    }

    @Test
    fun testTimeElapsedLessThan24HoursKeepsInitFailed() {
        // Version unchanged
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Failure timestamp is less than 24 hours (23 hours ago)
        val failureTime = System.currentTimeMillis() - (23 * 60 * 60 * 1000)
        whenever(mockPrefs.getLong(eq("last_failure_timestamp"), anyLong())).thenReturn(failureTime)

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences edit for resetting is NOT called
        verify(mockEditor, never()).putBoolean("init_failed", false)
        verify(mockEditor, never()).putLong("last_failure_timestamp", 0L)
    }

    @Test
    fun testResetInitFailedUpdatesTimestampToZero() {
        VlcPlayerBackend.resetInitFailed(mockContext)

        // Verify that resetInitFailed resets init_failed and last_failure_timestamp to 0L
        verify(mockEditor).putBoolean("init_failed", false)
        verify(mockEditor).putLong("last_failure_timestamp", 0L)
        verify(mockEditor).apply()
    }

    @Test
    fun testMarkInitFailedUpdatesTimestampToCurrentTime() {
        VlcPlayerBackend.markInitFailed(mockContext)

        // Verify that markInitFailed sets init_failed to true and saves a timestamp
        verify(mockEditor).putBoolean("init_failed", true)
        verify(mockEditor).putLong(eq("last_failure_timestamp"), anyLong())
        verify(mockEditor).apply()
    }

    @Test
    fun testRobustnessWithCrashingMockContext() {
        val badContext = mock(Context::class.java)
        // Make context calls throw exceptions to test fallback try-catch safety
        whenever(badContext.applicationContext).thenThrow(RuntimeException("Mock crash"))
        whenever(badContext.packageName).thenThrow(RuntimeException("Mock crash"))
        whenever(badContext.packageManager).thenThrow(RuntimeException("Mock crash"))
        whenever(badContext.getSharedPreferences(anyString(), anyInt())).thenThrow(RuntimeException("Mock crash"))

        // Should not throw exceptions
        VlcPlayerBackend.restoreInitFailedState(badContext)
        VlcPlayerBackend.resetInitFailed(badContext)
        VlcPlayerBackend.markInitFailed(badContext)
    }

    @Test
    fun testVersionDecrementDoesNotResetInitFailed() {
        // Stored version: 2L, stored status: init failed is true
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(2L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)
        
        // Failure timestamp is 1 hour ago (not expired)
        val failureTime = System.currentTimeMillis() - (1 * 60 * 60 * 1000)
        whenever(mockPrefs.getLong(eq("last_failure_timestamp"), anyLong())).thenReturn(failureTime)

        // Current version is decremented to 1L
        mockPackageInfo.versionCode = 1
        mockPackageInfo.longVersionCode = 1L

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences does NOT reset init_failed
        verify(mockEditor, never()).putBoolean(eq("init_failed"), eq(false))
        
        // Verify via reflection that the in-memory flag is still true
        val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
        initFailedField.isAccessible = true
        val initFailedValue = initFailedField.get(null) as Boolean
        assertTrue("initFailed in-memory flag should remain true", initFailedValue)
    }

    @Test
    fun testTimeElapsed23Hours59MinutesDoesNotTriggerReset() {
        // Version unchanged
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Failure timestamp is 23 hours 59 minutes ago
        val failureTime = System.currentTimeMillis() - (23 * 60 * 60 * 1000 + 59 * 60 * 1000)
        whenever(mockPrefs.getLong(eq("last_failure_timestamp"), anyLong())).thenReturn(failureTime)

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences resets init_failed is NOT called
        verify(mockEditor, never()).putBoolean(eq("init_failed"), eq(false))

        // Verify via reflection that the in-memory flag is still true
        val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
        initFailedField.isAccessible = true
        val initFailedValue = initFailedField.get(null) as Boolean
        assertTrue("initFailed in-memory flag should remain true", initFailedValue)
    }

    @Test
    fun testTimeElapsed24Hours1MinuteTriggersReset() {
        // Version unchanged
        whenever(mockPrefs.getLong(eq("version_code"), anyLong())).thenReturn(1L)
        whenever(mockPrefs.getBoolean(eq("init_failed"), anyBoolean())).thenReturn(true)

        // Failure timestamp is 24 hours 1 minute ago
        val failureTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000 + 1 * 60 * 1000)
        whenever(mockPrefs.getLong(eq("last_failure_timestamp"), anyLong())).thenReturn(failureTime)

        VlcPlayerBackend.restoreInitFailedState(mockContext)

        // Verify that SharedPreferences resets init_failed, version_code, and last_failure_timestamp
        verify(mockEditor).putBoolean("init_failed", false)
        verify(mockEditor).putLong("last_failure_timestamp", 0L)
        verify(mockEditor).apply()

        // Verify via reflection that the in-memory flag is now false
        val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
        initFailedField.isAccessible = true
        val initFailedValue = initFailedField.get(null) as Boolean
        assertFalse("initFailed in-memory flag should be false", initFailedValue)
    }

    @Test
    fun testRobustnessWithNullPackageManagerAndPackageName() {
        val badContext = mock(Context::class.java)
        whenever(badContext.applicationContext).thenReturn(badContext)
        whenever(badContext.packageName).thenReturn(null)
        whenever(badContext.packageManager).thenReturn(null)
        whenever(badContext.getSharedPreferences(anyString(), anyInt())).thenThrow(RuntimeException("Mock shared prefs error"))

        // Should not crash the app, safely defaults initFailed to false
        VlcPlayerBackend.restoreInitFailedState(badContext)
        val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
        initFailedField.isAccessible = true
        val initFailedValue = initFailedField.get(null) as Boolean
        assertFalse(initFailedValue)

        VlcPlayerBackend.resetInitFailed(badContext)
        VlcPlayerBackend.markInitFailed(badContext)
    }

    @Test
    fun testRobustnessWithPackageNameNotFoundException() {
        val badContext = mock(Context::class.java)
        val badPackageManager = mock(PackageManager::class.java)
        
        whenever(badContext.applicationContext).thenReturn(badContext)
        whenever(badContext.packageName).thenReturn("com.example.error")
        whenever(badContext.packageManager).thenReturn(badPackageManager)
        whenever(badContext.getSharedPreferences(anyString(), anyInt())).thenThrow(RuntimeException("Mock shared prefs error"))

        // Mock packageManager to throw NameNotFoundException
        try {
            whenever(badPackageManager.getPackageInfo(eq("com.example.error"), anyInt()))
                .thenThrow(PackageManager.NameNotFoundException("Mock name not found"))
        } catch (_: Exception) {}
        try {
            whenever(badPackageManager.getPackageInfo(eq("com.example.error"), any<PackageManager.PackageInfoFlags>()))
                .thenThrow(PackageManager.NameNotFoundException("Mock name not found"))
        } catch (_: Exception) {}

        // Should not crash the app, safely defaults initFailed to false
        VlcPlayerBackend.restoreInitFailedState(badContext)
        val initFailedField = VlcPlayerBackend::class.java.getDeclaredField("initFailed")
        initFailedField.isAccessible = true
        val initFailedValue = initFailedField.get(null) as Boolean
        assertFalse(initFailedValue)

        VlcPlayerBackend.resetInitFailed(badContext)
        VlcPlayerBackend.markInitFailed(badContext)
    }

    @Test
    fun testPlayerViewSetterRebinding() {
        val playerBackend = VlcPlayerBackend()
        
        // Mock PlayerView
        val mockPlayerView1 = mock(androidx.media3.ui.PlayerView::class.java)
        val mockPlayerView2 = mock(androidx.media3.ui.PlayerView::class.java)
        val mockSurfaceView = mock(android.view.SurfaceView::class.java)
        val mockHolder = mock(android.view.SurfaceHolder::class.java)
        val mockSurface = mock(android.view.Surface::class.java)

        whenever(mockSurfaceView.holder).thenReturn(mockHolder)
        whenever(mockHolder.surface).thenReturn(mockSurface)
        
        // Mock ViewGroup child lookup
        whenever(mockPlayerView2.childCount).thenReturn(1)
        whenever(mockPlayerView2.getChildAt(0)).thenReturn(mockSurfaceView)
        
        // Set playerView initially (playback is idle, mediaPlayer is null)
        playerBackend.playerView = mockPlayerView1
        assertEquals(mockPlayerView1, playerBackend.playerView)
        
        // Setup fake media player and vout
        val fakeVout = FakeVout()
        val fakeMediaPlayer = FakeMediaPlayer(fakeVout)
        
        // Set mock mediaPlayer via reflection
        val mpField = VlcPlayerBackend::class.java.getDeclaredField("mediaPlayer")
        mpField.isAccessible = true
        mpField.set(playerBackend, fakeMediaPlayer)
        
        // Change playerView to mockPlayerView2 during "active" playback
        playerBackend.playerView = mockPlayerView2
        
        // Assertions
        assertEquals(mockPlayerView2, playerBackend.playerView)
        assertTrue(fakeVout.detachViewsCalled)
        assertTrue(fakeVout.setVideoSurfaceCalled)
        assertTrue(fakeVout.attachViewsCalled)
    }

    class FakeVout {
        var detachViewsCalled = false
        var attachViewsCalled = false
        var setVideoSurfaceCalled = false
        
        fun detachViews() {
            detachViewsCalled = true
        }
        
        fun setVideoSurface(surface: android.view.Surface?, holder: android.view.SurfaceHolder?) {
            setVideoSurfaceCalled = true
        }
        
        fun attachViews() {
            attachViewsCalled = true
        }
    }

    class FakeMediaPlayer(private val vout: FakeVout) {
        fun getVLCVout(): FakeVout = vout
        fun isPlaying(): Boolean = true
    }
}


