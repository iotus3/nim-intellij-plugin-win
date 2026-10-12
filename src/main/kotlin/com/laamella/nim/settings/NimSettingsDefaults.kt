package com.laamella.nim.settings

import com.intellij.openapi.util.SystemInfo

/**
 * Factory for the default values of [NimSettings]. The executable fields start blank: for the
 * language server that means "nim check on save", for the others "look the bare name up on PATH".
 */
object NimSettingsDefaults {
    fun nimSdkPath(): String = ""
    fun nimbleToolchainPath(): String = ""
    fun nimlangserverExe(): String = ""
    fun nimbleExe(): String = ""
    fun formatterExe(): String = ""

    /** Name used when an executable field is blank; `pathWithToolchain()` resolves it on PATH. */
    fun exeName(tool: String): String = if (SystemInfo.isWindows) "$tool.exe" else tool
}
