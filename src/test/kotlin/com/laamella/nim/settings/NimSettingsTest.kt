package com.laamella.nim.settings

import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class NimSettingsTest {
    private val ext = if (SystemInfo.isWindows) ".exe" else ""

    @Test fun `nim is derived from the sdk bin`() {
        val settings = NimSettings().also { it.nimSdkPath = "/opt/nim" }
        assertEquals(Path.of("/opt/nim", "bin", "nim$ext").toString(), settings.nim())
    }

    @Test fun `nim falls back to a bare name without an sdk`() {
        assertEquals("nim$ext", NimSettings().nim())
    }

    @Test fun `exe getters return stored absolute paths`() {
        val settings = NimSettings().also {
            it.nimlangserverExe = "/x/nimlangserver"
            it.nimbleExe = "/x/nimble"
            it.formatterExe = "/x/nph"
        }
        assertEquals("/x/nimlangserver", settings.nimlangserver())
        assertEquals("/x/nimble", settings.nimble())
        assertEquals("/x/nph", settings.formatter())
    }

    @Test fun `exe getters fall back to bare names when blank`() {
        val settings = NimSettings()
        assertEquals("nimlangserver$ext", settings.nimlangserver())
        assertEquals("nimble$ext", settings.nimble())
        assertEquals("nimpretty$ext", settings.formatter())
    }

    @Test fun `effective toolchain path defaults to home nimble bin`() {
        val settings = NimSettings().also { it.nimbleToolchainPath = "" }
        assertEquals(
            Path.of(System.getProperty("user.home"), ".nimble", "bin").toString(),
            settings.effectiveToolchainPath()
        )
    }

    @Test fun `pkgs2Dir sits next to the toolchain bin directory`() {
        val settings = NimSettings().also { it.nimbleToolchainPath = "/home/u/.nimble/bin" }
        assertEquals(Path.of("/home/u/.nimble", "pkgs2"), settings.pkgs2Dir())
    }

    @Test fun `pathWithToolchain prepends toolchain and sdk bin`() {
        val settings = NimSettings().also {
            it.nimbleToolchainPath = "/home/u/.nimble/bin"
            it.nimSdkPath = "/opt/nim"
        }
        val parts = settings.pathWithToolchain().split(File.pathSeparator)
        assertEquals("/home/u/.nimble/bin", parts[0])
        assertEquals(Path.of("/opt/nim", "bin").toString(), parts[1])
    }

    @Test fun `legacy nimbleBinPath migrates as-is to the toolchain path`() {
        val legacyBin = Path.of("/home/u/.nimble", "bin").toString()
        val legacy = NimSettings().also {
            it.nimbleBinPath = legacyBin
            it.nimbleToolchainPath = ""
            it.nimbleExe = "nimble$ext" // bare file name, must be dropped
        }
        val target = NimSettings()
        target.loadState(legacy)
        assertEquals(legacyBin, target.nimbleToolchainPath)
        assertEquals("", target.nimbleBinPath)
        assertEquals("", target.nimbleExe)
    }

    @Test fun `loadState keeps absolute exe paths`() {
        val absolute = Path.of(System.getProperty("user.home"), "nimble").toString()
        val legacy = NimSettings().also { it.nimbleExe = absolute }
        val target = NimSettings()
        target.loadState(legacy)
        assertEquals(absolute, target.nimbleExe)
    }

    @Test fun `applyDetected can leave the language server untouched`() {
        val settings = NimSettings()
        settings.applyDetected(
            NimToolchainDetector.Detected(
                sdkPath = "/sdk", toolchainPath = "/tc",
                langServer = "/ls", nimble = "/n", formatter = "/f"
            ),
            overwriteLangServer = false
        )
        assertEquals("", settings.nimlangserverExe)
        assertEquals("/n", settings.nimbleExe)
        assertEquals("/f", settings.formatterExe)
    }
}
