package com.github.winteryoung.gwallpaper

import org.joda.time.DateTime
import org.joda.time.Duration
import org.slf4j.LoggerFactory

/**
 * @author Winter Young
 * @since 2017/5/23
 */

private val log = LoggerFactory.getLogger("Utils")

fun <T> timedSecs(msg: String, block: () -> T): T {
    val start = DateTime.now()
    try {
        return block()
    } finally {
        val duration = Duration(start, DateTime.now())
        log.warn("Duration: ${duration.standardSeconds} seconds, $msg")
    }
}