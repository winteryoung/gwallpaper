package com.github.winteryoung.gwallpaper.utils

import org.joda.time.DateTime
import org.slf4j.LoggerFactory.getLogger

/**
 * @author Winter Young
 * @since 2017/5/23
 */

private val log = getLogger("Utils")

fun <T> timedSecs(msg: String, block: () -> T): T {
    val start = DateTime.now()
    try {
        return block()
    } finally {
        val duration = org.joda.time.Duration(start, DateTime.now())
        log.warn("$msg: ${duration.standardSeconds} seconds")
    }
}