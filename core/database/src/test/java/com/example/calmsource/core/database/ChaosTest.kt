package com.example.calmsource.core.database

import com.example.calmsource.core.database.entity.IPTVProviderEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ChaosTest {

    @Test
    fun testRoomEntitiesHaveNoForeignKeys() {
        // We verify that XtreamVodEntity has no foreign keys configured.
        val annotations = XtreamVodEntity::class.java.annotations
        val entityAnnotation = annotations.find { it.annotationClass.simpleName == "Entity" }
        // If it had foreignKeys, it would be in the @Entity annotation. We can just check the annotation properties via reflection.
        val entityStr = entityAnnotation.toString()
        val hasForeignKeys = entityStr.contains("foreignKeys=[@androidx.room.ForeignKey")
        assertEquals("XtreamVodEntity should not have foreign keys (this confirms the bug)", false, hasForeignKeys)
    }
}
