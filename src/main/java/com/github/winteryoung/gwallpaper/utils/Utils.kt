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
        log.info("$msg: ${duration.standardSeconds} seconds")
    }
}

data class Stoppable<out Element> constructor(val _continue: Boolean, val element: Element?) {
    companion object {
        fun <Element> _continue(element: Element) = Stoppable(true, element)
        fun _break() = Stoppable(false, null)
    }
}

fun <Accumulator, Element> Sequence<Element>.stoppableFold(
        initValue: Accumulator?,
        operation: (Accumulator?, Element) -> Stoppable<Accumulator?>
): Accumulator? {
    var lastValue = initValue
    for (element in this) {
        val (_continue, acc) = operation(lastValue, element)
        if (_continue) {
            lastValue = acc
        } else {
            break
        }
    }
    return lastValue
}