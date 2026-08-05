/*
 * Copyright 2026 Kyriakos Georgiopoulos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grafima.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * Derives every desktop icon format from one square PNG.
 *
 * Written against ImageIO rather than `sips` and `iconutil` so it runs on any
 * host, not just the Mac the artwork happens to come from.
 */
abstract class GenerateIcons : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputFile
    abstract val squareIcon: RegularFileProperty

    @get:OutputFile
    abstract val macOsIcon: RegularFileProperty

    @get:OutputFile
    abstract val icns: RegularFileProperty

    @get:OutputFile
    abstract val ico: RegularFileProperty

    @TaskAction
    fun generate() {
        val artwork = ImageIO.read(source.get().asFile)
        require(artwork.width == artwork.height) { "Source artwork must be square" }

        val rounded = roundedForMacOs(artwork)
        // Copied rather than re-encoded: the artwork is already the PNG this
        // needs, and round-tripping it only adds an opaque alpha channel.
        write(squareIcon, source.get().asFile.readBytes())
        write(macOsIcon, scaledPng(rounded, rounded.width))
        write(icns, icns(rounded))
        write(ico, ico(artwork))
    }

    private fun write(target: RegularFileProperty, bytes: ByteArray) {
        target.get().asFile.apply { parentFile.mkdirs() }.writeBytes(bytes)
    }

    /**
     * macOS draws app icons unmasked, so the artwork has to carry the rounded
     * shape itself: an 824px body centred on a 1024px canvas, per Apple's grid.
     */
    private fun roundedForMacOs(artwork: BufferedImage): BufferedImage {
        val canvas = 1024
        val body = 824
        val radius = 185f
        val inset = ((canvas - body) / 2).toFloat()

        return BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB).also { target ->
            with(target.createGraphics()) {
                applyQualityHints()
                clip = RoundRectangle2D.Float(inset, inset, body.toFloat(), body.toFloat(), radius * 2, radius * 2)
                drawImage(artwork, inset.toInt(), inset.toInt(), body, body, null)
                dispose()
            }
        }
    }

    private fun scaledPng(artwork: BufferedImage, size: Int): ByteArray {
        val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        with(scaled.createGraphics()) {
            applyQualityHints()
            drawImage(artwork, 0, 0, size, size, null)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(scaled, "png", it) }.toByteArray()
    }

    private fun Graphics2D.applyQualityHints() {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    /** `icns` magic and total length, then an 8-byte header and a PNG per entry. Big-endian. */
    private fun icns(artwork: BufferedImage): ByteArray {
        val entries = listOf(
            "icp4" to 16, "icp5" to 32, "ic11" to 32, "ic12" to 64, "ic07" to 128,
            "ic13" to 256, "ic08" to 256, "ic14" to 512, "ic09" to 512, "ic10" to 1024
        )
        val body = ByteArrayOutputStream()
        entries.forEach { (type, size) ->
            val png = scaledPng(artwork, size)
            body.write(type.toByteArray(Charsets.US_ASCII))
            body.write(ByteBuffer.allocate(4).putInt(8 + png.size).array())
            body.write(png)
        }
        return ByteArrayOutputStream().apply {
            write("icns".toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(4).putInt(8 + body.size()).array())
            write(body.toByteArray())
        }.toByteArray()
    }

    /** ICONDIR, then a 16-byte entry per size, then the PNGs they point at. Little-endian. */
    private fun ico(artwork: BufferedImage): ByteArray {
        val images = listOf(16, 32, 48, 64, 128, 256).map { it to scaledPng(artwork, it) }
        val directory = ByteArrayOutputStream()
        val body = ByteArrayOutputStream()
        var offset = 6 + 16 * images.size

        images.forEach { (size, png) ->
            // 256 does not fit in a byte and is encoded as 0.
            val dimension = if (size >= 256) 0 else size
            directory.write(
                ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .put(dimension.toByte()).put(dimension.toByte())
                    .put(0.toByte()).put(0.toByte())
                    .putShort(1.toShort()).putShort(32.toShort())
                    .putInt(png.size).putInt(offset)
                    .array()
            )
            body.write(png)
            offset += png.size
        }

        val header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0.toShort()).putShort(1.toShort()).putShort(images.size.toShort())
            .array()

        return ByteArrayOutputStream().apply {
            write(header)
            write(directory.toByteArray())
            write(body.toByteArray())
        }.toByteArray()
    }
}
