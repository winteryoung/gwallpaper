package com.github.winteryoung.gwallpaper

import com.github.winteryoung.gwallpaper.utils.*
import com.github.winteryoung.gwallpaper.win32.User32
import kotlinx.coroutines.experimental.runBlocking
import org.apache.commons.lang3.RandomUtils
import org.apache.http.client.fluent.Request
import org.joda.time.DateTime
import org.joda.time.Duration
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO


/**
 * @author Winter Young
 * @since 2017/5/23
 */
object Main {
    private val log = LoggerFactory.getLogger(javaClass)

    private val connectTimeout = TimeUnit.MILLISECONDS.convert(2, TimeUnit.SECONDS).toInt()
    private val readTimeout = TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS).toInt()
    private val galleryWaitTimeout = TimeUnit.SECONDS.convert(2, TimeUnit.SECONDS).toInt()

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val file = crawlImage()
        setWallPaper(file)
    }

    private fun setWallPaper(file: File) {
        val winIni = User32.SPIF_UPDATEINIFILE or User32.SPIF_SENDWININICHANGE
        val success = User32.INSTANCE.SystemParametersInfoA(
                User32.SPI_SETDESKWALLPAPER,
                0,
                file.absolutePath,
                winIni)
        if (success) {
            log.warn("Done setting wallpaper")
        } else {
            throw Exception("Setting wallpaper failed")
        }
    }

    private suspend fun crawlImage(): File {
        return chromeDriver().use { crawlImage(it) }
    }

    private suspend fun crawlImage(webDriver: WebDriver): File {
        webDriver.get("http://image.google.com")

        val input = webDriver.findElementByCss("#lst-ib")
        val screenSize = Toolkit.getDefaultToolkit().screenSize.run {
            width to height
        }
        input.sendKeys("nature wallpapers ${screenSize.first}x${screenSize.second}")
        input.sendKeys(Keys.ENTER)

        val maxLen = expandGallery(webDriver)
        log.warn("Max image: $maxLen")

        return crawlImageInListPage(webDriver, maxLen, screenSize)
    }

    private suspend fun crawlImageInListPage(webDriver: WebDriver, maxLen: Int, screenSize: ScreenSize): File {
        val (url, bytes) = crawlImageUrlInListPage(webDriver, maxLen, screenSize, setOf())
        val fileExt = "." + url.substringAfterLast('.')
        return File.createTempFile("gwallpaper-", fileExt).apply {
            deleteOnExit()
            writeBytes(bytes)
            log.warn("Image crawled and stored: $this")
        }
    }

    private tailrec suspend fun crawlImageUrlInListPage(
            webDriver: WebDriver,
            maxLen: Int,
            screenSize: ScreenSize,
            excludedIndexes: Set<Int>
    ): Pair<String, ByteArray> {
        val imageIndex = RandomUtils.nextInt(1, maxLen)
        if (excludedIndexes.contains(imageIndex)) {
            log.warn("Visited image, try another")
            return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes)
        }

        log.warn("Visit image $imageIndex")

        val img = webDriver.findElementByCss("#rg_s > div:nth-child($imageIndex) > a > img")
        img.click()

        val viewImageButton = webDriver.findElementByCss("#irc_cc > div:nth-child(2) > div.irc_b.i8152.irc_mmc > div.i30053 > div > div.irc_butc > table._Ccb.irc_but_r > tbody > tr > td:nth-child(2) > a")
        val url = viewImageButton.getAttribute("href")
        log.warn("url: $url")

        val size = testImageSize(url)
                ?: return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))

        if (size != screenSize) {
            log.warn("Wrong image size ($size), try another")
            return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))
        }

        val bytes = readImageBytes(url)
                ?: return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))

        return url to bytes
    }

    private fun readImageBytes(url: String?): ByteArray? {
        return timedSecs("read image bytes") {
            try {
                Request.Get(url)
                        .connectTimeout(connectTimeout)
                        .socketTimeout(readTimeout)
                        .execute()
                        .returnContent()
                        .asBytes()
            } catch(e: IOException) {
                log.warn("Error read url: $url, reason: ${e.message}")
                return@timedSecs null
            }
        }
    }

    private fun testImageSize(url: String): Pair<Int, Int>? {
        val inputStream = timedSecs("test image size") {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = this@Main.connectTimeout
                    readTimeout = this@Main.readTimeout
                }.getInputStream().buffered(128)
            } catch (e: IOException) {
                log.warn("Cannot open stream for url: $url, reason: ${e.message}")
                return@timedSecs null
            }
        } ?: return null

        return testImageSize(inputStream).apply {
            if (this == null) {
                log.warn("Cannot get image size: $url")
            }
        }
    }

    private fun testImageSize(inputStream: InputStream): Pair<Int, Int>? {
        ImageIO.createImageInputStream(inputStream).use { istream ->
            val readers = ImageIO.getImageReaders(istream)
            if (readers.hasNext()) {
                val reader = readers.next()
                try {
                    reader.input = istream
                    return reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }

        return null
    }

    private suspend fun expandGallery(webDriver: WebDriver): Int {
        var maxLen = 0
        var maxLenSet = DateTime.now()

        while (true) {
            val jsEngine = webDriver as JavascriptExecutor
            jsEngine.executeScript("window.scrollBy(0,5000)")
            val divs = webDriver.findElementsByCss("#rg_s > div")
            if (divs.size > maxLen) {
                maxLen = divs.size
                maxLenSet = DateTime.now()
            } else {
                val elapsed = Duration(maxLenSet, DateTime.now())
                if (elapsed.standardSeconds > galleryWaitTimeout) {
                    break
                }
            }
        }

        return maxLen
    }
}
