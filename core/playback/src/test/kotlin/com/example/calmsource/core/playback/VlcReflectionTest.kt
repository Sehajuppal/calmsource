package com.example.calmsource.core.playback

import android.content.Context
import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class VlcReflectionTest {

    @Test
    fun inspectVlcClasses() {
        println("=== INSPECTING VLC CLASSES ===")
        try {
            val libVlcClass = Class.forName("org.videolan.libvlc.LibVLC")
            val iLibVlcClass = Class.forName("org.videolan.libvlc.interfaces.ILibVLC")
            val mediaClass = Class.forName("org.videolan.libvlc.Media")
            val playerClass = Class.forName("org.videolan.libvlc.MediaPlayer")

            // Test LibVLC constructor resolution
            val vlcConstructor = libVlcClass.getConstructor(Context::class.java, List::class.java)
            println("Resolved LibVLC constructor: $vlcConstructor")

            // Test Media constructor resolution
            val mediaConstructor = mediaClass.getConstructor(iLibVlcClass, android.net.Uri::class.java)
            println("Resolved Media constructor: $mediaConstructor")

            // Test MediaPlayer constructor resolution
            val playerConstructor = playerClass.getConstructor(iLibVlcClass)
            println("Resolved MediaPlayer constructor: $playerConstructor")

            println("MediaPlayer methods containing Vout or vout:")
            playerClass.methods.forEach { method ->
                if (method.name.contains("vout", ignoreCase = true)) {
                    println("  $method")
                }
            }

            println("Media methods:")
            mediaClass.methods.forEach { method ->
                if (method.name in listOf("addOption")) {
                    println("  $method")
                }
            }

            val eventClass = Class.forName("org.videolan.libvlc.MediaPlayer\$Event")
            var currentSuper: Class<*>? = eventClass
            while (currentSuper != null && currentSuper != Any::class.java) {
                println("Class/Superclass ${currentSuper.name} Declared Fields:")
                currentSuper.declaredFields.forEach { println("  $it") }
                println("Class/Superclass ${currentSuper.name} Declared Methods:")
                currentSuper.declaredMethods.forEach { println("  $it") }
                currentSuper = currentSuper.superclass
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
