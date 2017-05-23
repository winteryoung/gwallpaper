package com.github.winteryoung.gwallpaper.win32

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary


/**
 * @author Winter Young
 * @since 2017/5/23
 */
interface User32 : StdCallLibrary {
    fun SystemParametersInfoA(
            uiAction: Int,
            uiParam: Int,
            pvParam: String,
            fWinIni: Int
    ): Boolean

    companion object {
        val INSTANCE = Native.loadLibrary("user32", User32::class.java)

        val SPI_SETDESKWALLPAPER = 0x0014
        val SPIF_UPDATEINIFILE = 0x01
        val SPIF_SENDWININICHANGE = 0x02
    }
}