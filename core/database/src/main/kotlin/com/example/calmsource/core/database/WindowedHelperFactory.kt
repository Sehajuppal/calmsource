package com.example.calmsource.core.database

import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQuery
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * [SupportSQLiteOpenHelper.Factory] that delegates to [FrameworkSQLiteOpenHelperFactory].
 *
 * Previously this tried to inject a custom CursorFactory via
 * `Configuration.toBuilder()`, but that API doesn't exist in the version
 * of `androidx.sqlite` used by this project. Instead we simply delegate
 * directly — the 8 MB cursor window goal can be achieved at a higher
 * level (e.g., `CursorWindowCompat` or per-query workarounds).
 *
 * **Warning regarding modern Android API restrictions (`sCursorWindowSize`):**
 * Starting with Android 9 (API 28), reflection on non-SDK interfaces (hidden fields) is
 * restricted/blocked by the platform. Consequently, attempting to modify `sCursorWindowSize`
 * via reflection on API 28+ will result in a [NoSuchFieldException] (which is safely caught
 * by [runCatching]). Since this block is bypassed/skipped or fails on API 28+, the cursor window
 * size remains at its system default (typically ~2MB) on Android 9 and newer.
 *
 * To avoid issues with large queries exceeding the default window size on newer API levels,
 * the following recommended strategies should be used:
 * 1. **Pagination:** Load data in smaller, chunked pages using Room's paging support (e.g., Paging Library)
 *    or raw `LIMIT`/`OFFSET` queries.
 * 2. **Projections:** Only select columns that are actually needed for the current UI/operation to minimize
 *    the memory footprint of each row.
 * 3. **Blob Storage:** Store large binary assets (such as images, files, or large payloads) externally in the
 *    filesystem or cache directory, and only store their local file paths in the database.
 */
class WindowedHelperFactory(
    private val cursorWindowSizeBytes: Long = DEFAULT_CURSOR_WINDOW_BYTES
) : SupportSQLiteOpenHelper.Factory {

    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()

    init {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            runCatching {
                val field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
                field.isAccessible = true
                if (field.type == Long::class.javaPrimitiveType || field.type == Long::class.java) {
                    field.set(null, cursorWindowSizeBytes)
                } else {
                    field.set(null, cursorWindowSizeBytes.toInt())
                }
            }
        }
    }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return delegate.create(configuration)
    }

    companion object {
        const val DEFAULT_CURSOR_WINDOW_BYTES: Long = 8L * 1024 * 1024 // 8 MB
    }
}
