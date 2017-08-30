package com.github.winteryoung.gwallpaper.utils

import org.openqa.selenium.Proxy
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.DesiredCapabilities
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * @author Winter Young
 * @since 2017/5/23
 */
private val log = LoggerFactory.getLogger("WebDriverUtils")

fun chromeDriver(proxy: String? = null): ChromeDriver {
    System.setProperty("webdriver.chrome.silentOutput", "true")
    return ChromeDriver(DesiredCapabilities.chrome().apply {
        if (proxy != null) {
            setCapability("proxy", Proxy().setHttpProxy(proxy))
        }
        setCapability(ChromeOptions.CAPABILITY, ChromeOptions().apply {
            addArguments("--headless")
        })
    }).apply {
        manage().apply {
            timeouts().implicitlyWait(40, TimeUnit.SECONDS)
        }
    }
}

fun <T> WebDriver.use(logError: Boolean = false, block: (WebDriver) -> T): T {
    try {
        return block(this)
    } catch (e: Throwable) {
        if (logError) {
            log.error("WebDriver quit", e)
        }
        throw e
    } finally {
        try {
            quit()
        } catch (e: Throwable) {
            log.warn("Error quitting web driver", e)
        }
    }
}