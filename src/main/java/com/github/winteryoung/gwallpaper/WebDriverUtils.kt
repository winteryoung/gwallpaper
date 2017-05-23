package com.github.winteryoung.gwallpaper

import com.gargoylesoftware.htmlunit.BrowserVersion
import com.gargoylesoftware.htmlunit.WebClient
import kotlinx.coroutines.experimental.delay
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.DesiredCapabilities
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

fun htmlUnitDriver(): HtmlUnitDriver {
    return object : HtmlUnitDriver(DesiredCapabilities.htmlUnitWithJs().apply {
        setCapability(CapabilityType.PROXY, Proxy().apply {
            httpProxy = "localhost:7777"
        })
    }) {
        override fun newWebClient(version: BrowserVersion?): WebClient {
            val webClient = super.newWebClient(version)
            webClient.options.isThrowExceptionOnScriptError = false
            return webClient
        }
    }.apply {
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

fun WebDriver.findElementsByCss(
        selector: String,
        timeOut: Long = 10,
        unit: TimeUnit = TimeUnit.SECONDS
): List<WebElement> {
    val cssSelector = By.cssSelector(selector)
    WebDriverWait(this, TimeUnit.SECONDS.convert(timeOut, unit))
            .until(ExpectedConditions.visibilityOfElementLocated(cssSelector))
    return findElements(cssSelector)
}

fun WebDriver.findElementByCss(
        selector: String,
        timeOut: Long = 10,
        unit: TimeUnit = TimeUnit.SECONDS,
        resultSelector: (List<WebElement>) -> WebElement = List<WebElement>::last
): WebElement {
    val results = findElementsByCss(selector, timeOut, unit)
    return resultSelector(results)
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