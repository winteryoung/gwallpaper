package com.github.winteryoung.gwallpaper.utils

import kotlinx.coroutines.experimental.delay
import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * @author Winter Young
 * @since 2017/5/23
 */
private val log = LoggerFactory.getLogger("WebDriverUtils")

fun chromeDriver(): ChromeDriver {
    System.setProperty("webdriver.chrome.silentOutput", "true")
    return ChromeDriver(ChromeOptions().apply {
        addArguments("--proxy-server=http://localhost:7777")
    }).apply {
        manage().apply {
            timeouts().implicitlyWait(40, TimeUnit.SECONDS)
        }
    }
}

suspend fun <T> WebDriver.exec(block: suspend WebDriver.() -> T): T {
    var ex: Exception? = null
    repeat(10) {
        try {
            return block()
        } catch (e: StaleElementReferenceException) {
            log.info("StaleElementReferenceException occurred")
            ex = e
            delay(200, TimeUnit.MILLISECONDS)
        }
    }
    throw Exception("Failed due to StaleElementReferenceException", ex)
}

suspend fun WebDriver.findElementsByCss(
        selector: String,
        timeOut: Long = 10,
        unit: TimeUnit = TimeUnit.SECONDS
): List<WebElement> {
    return exec {
        val cssSelector = By.cssSelector(selector)
        WebDriverWait(this, TimeUnit.SECONDS.convert(timeOut, unit))
                .until(ExpectedConditions.visibilityOfElementLocated(cssSelector))
        return@exec findElements(cssSelector)
    }
}

suspend fun WebDriver.findElementByCss(
        selector: String,
        timeOut: Long = 10,
        unit: TimeUnit = TimeUnit.SECONDS,
        resultSelector: (List<WebElement>) -> WebElement = List<WebElement>::last
): WebElement {
    return exec {
        val results = findElementsByCss(selector, timeOut, unit)
        return@exec resultSelector(results)
    }
}

suspend fun <T> WebDriver.use(logError: Boolean = false, block: suspend (WebDriver) -> T): T {
    try {
        return block(this)
    } catch (e: Throwable) {
        if (logError) {
            log.error("WebDriver quit", e)
        }
        throw e
    } finally {
        close()
    }
}