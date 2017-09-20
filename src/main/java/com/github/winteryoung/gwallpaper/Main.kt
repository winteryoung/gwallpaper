package com.github.winteryoung.gwallpaper

import com.github.winteryoung.gwallpaper.utils.*
import com.github.winteryoung.gwallpaper.win32.User32
import org.apache.commons.lang3.RandomUtils
import org.apache.http.client.fluent.Request
import org.joda.time.DateTime
import org.joda.time.Duration
import org.openqa.selenium.By
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
    fun main(args: Array<String>) {
        crawlImage().let {
            setWallPaper(it)
        }
    }

    private fun setWallPaper(file: File) {
        (User32.SPIF_UPDATEINIFILE or User32.SPIF_SENDWININICHANGE).let { winIni ->
            User32.INSTANCE.SystemParametersInfoA(
                    User32.SPI_SETDESKWALLPAPER,
                    0,
                    file.absolutePath,
                    winIni)
        }.let { success ->
            if (success) {
                log.info("Done setting wallpaper")
            } else {
                throw Exception("Setting wallpaper failed")
            }
        }
    }

    private fun crawlImage(): File {
        return chromeDriver("127.0.0.1:7778").use { crawlImage(it) }
    }

    private fun crawlImage(webDriver: WebDriver): File {
        webDriver.get("https://images.google.com")

        webDriver.findElement(By.cssSelector("#lst-ib")).let { input ->
            Toolkit.getDefaultToolkit().screenSize.run {
                width to height
            }.let { screenSize ->
                log.info("Search")

                input.sendKeys("nature wallpapers ${screenSize.first}x${screenSize.second}")
                input.sendKeys(Keys.ENTER)

                expandGallery(webDriver).let { maxLen ->
                    log.info("Max image: $maxLen")
                    return crawlImageInListPage(webDriver, maxLen, screenSize)
                }
            }
        }
    }

    private fun crawlImageInListPage(webDriver: WebDriver, maxLen: Int, screenSize: ScreenSize): File {
        crawlImageUrlInListPage(webDriver, maxLen, screenSize, setOf()).let { (url, bytes) ->
            ("." + url.substringAfterLast('.')).let { fileExt ->
                return File.createTempFile("gwallpaper-", fileExt).apply {
                    deleteOnExit()
                    writeBytes(bytes)
                    log.info("Image crawled and stored: $this")
                }
            }
        }
    }

    private tailrec fun crawlImageUrlInListPage(
            webDriver: WebDriver,
            maxLen: Int,
            screenSize: ScreenSize,
            excludedIndexes: Set<Int>
    ): Pair<String, ByteArray> {
        RandomUtils.nextInt(1, maxLen).let { imageIndex ->
            if (excludedIndexes.contains(imageIndex)) {
                log.info("Visited image, try another")
                return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes)
            }

            log.info("Visit image $imageIndex")

            By.cssSelector("#rg_s > div:nth-child($imageIndex) > a > img").let {
                webDriver.findElement(it)
            }.click()

            readUrl(webDriver, maxLen, screenSize, excludedIndexes, imageIndex).let {
                return it
            }
        }
    }

    private fun readUrl(
            webDriver: WebDriver,
            maxLen: Int,
            screenSize: ScreenSize,
            excludedIndexes: Set<Int>,
            imageIndex: Int
    ): Pair<String, ByteArray> {
        By.cssSelector("#irc_cc > div:nth-child(2) > div.irc_b.i8152.irc_mmc > div.i30053 > div > div.irc_butc > table._Ccb.irc_but_r > tbody > tr > td:nth-child(2) > a").let {
            webDriver.findElement(it)
        }.getAttribute("href").let { url ->
            log.info("url: $url")
            readUrl(webDriver, url, maxLen, screenSize, excludedIndexes, imageIndex).let {
                return it
            }
        }
    }

    private fun readUrl(
            webDriver: WebDriver,
            url: String,
            maxLen: Int,
            screenSize: ScreenSize,
            excludedIndexes: Set<Int>,
            imageIndex: Int
    ): Pair<String, ByteArray> {
        testImageSize(url).also {
            if (it == null) {
                return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))
            }
        }.let { size ->
            if (size != screenSize) {
                log.info("Wrong image size ($size), try another")
                return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))
            }
        }

        readImageBytes(url).also {
            if (it == null) {
                return crawlImageUrlInListPage(webDriver, maxLen, screenSize, excludedIndexes.plus(imageIndex))
            }
        }.let { bytes ->
            return url to bytes!!
        }
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
            } catch (e: IOException) {
                log.info("Error read url: $url, reason: ${e.message}")
                return@timedSecs null
            }
        }
    }

    private fun testImageSize(url: String): Pair<Int, Int>? {
        timedSecs("test image size") {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = this@Main.connectTimeout
                    readTimeout = this@Main.readTimeout
                }.getInputStream().buffered(128)
            } catch (e: IOException) {
                log.info("Cannot open stream for url: $url, reason: ${e.message}")
                return@timedSecs null
            }
        }.also {
            if (it == null) {
                return null
            }
        }.let { inputStream ->
            testImageSize(inputStream!!).apply {
                if (this == null) {
                    log.info("Cannot get image size: $url")
                }
            }
        }.let {
            return it
        }
    }

    private fun testImageSize(inputStream: InputStream): Pair<Int, Int>? {
        ImageIO.createImageInputStream(inputStream).use { istream ->
            ImageIO.getImageReaders(istream).let { readers ->
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
        }
        return null
    }

    private fun expandGallery(webDriver: WebDriver): Int {
        generateSequence(0) {
            it + 1
        }.stoppableFold<Pair<Int, DateTime>?, Int>(0 to DateTime.now()) { acc, _ ->
            acc!!.let { (maxLen, maxLenSetTime) ->
                scrollPage(webDriver, maxLen, maxLenSetTime)
            }
        }!!.first.let {
            return it
        }
    }

    @Suppress("ReplaceSingleLineLet")
    private fun scrollPage(
            webDriver: WebDriver, maxLen: Int, maxLenSetTime: DateTime
    ): Stoppable<Pair<Int, DateTime>?> {
        (webDriver as JavascriptExecutor).let { jsEngine ->
            jsEngine.executeScript("window.scrollBy(0,5000)")
        }

        webDriver.findElements(By.cssSelector("#rg_s > div")).let { divs ->
            if (divs.size > maxLen) {
                Stoppable._continue(divs.size to DateTime.now())
            } else {
                val elapsed = Duration(maxLenSetTime, DateTime.now())
                if (elapsed.standardSeconds > galleryWaitTimeout) {
                    Stoppable._break()
                } else {
                    Stoppable._continue(maxLen to maxLenSetTime)
                }
            }
        }.let {
            return it
        }
    }
}
