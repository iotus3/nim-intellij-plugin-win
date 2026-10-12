package com.laamella.nim.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NimToolchainDetectorTest {

    // ---- where / which parsing ------------------------------------------

    @Test fun `parseWhereOutput keeps windows absolute paths and strips quotes`() {
        val output = "INFO: Could not find files for the given pattern.\r\n" +
            "C:\\Nim\\bin\\nim.exe\r\n" +
            "\"C:\\Program Files\\Nim\\bin\\nim.exe\"\r\n"
        assertEquals(
            listOf(Path.of("C:\\Nim\\bin\\nim.exe"), Path.of("C:\\Program Files\\Nim\\bin\\nim.exe")),
            NimToolchainDetector.parseWhereOutput(output)
        )
    }

    @Test fun `parseWhereOutput returns empty for error output`() {
        assertEquals(emptyList(), NimToolchainDetector.parseWhereOutput("INFO: Could not find files.\n"))
    }

    // Regression for the macOS bug: `whereis` there prints a bare path with no "nim:" label.
    @Test fun `parseWhichOutput keeps a macOS style bare path`() {
        assertEquals(
            listOf(Path.of("/usr/bin/nim")),
            NimToolchainDetector.parseWhichOutput("/usr/bin/nim\n")
        )
    }

    @Test fun `parseWhichOutput handles multiline and strips quotes`() {
        assertEquals(
            listOf(Path.of("/usr/local/bin/nim"), Path.of("/opt/nim/bin/nim")),
            NimToolchainDetector.parseWhichOutput("/usr/local/bin/nim\n\"/opt/nim/bin/nim\"\n")
        )
        assertEquals(emptyList(), NimToolchainDetector.parseWhichOutput(""))
    }

    @Test fun `parseWhichOutput ignores not-found messages and bare names`() {
        assertEquals(emptyList(), NimToolchainDetector.parseWhichOutput("which: no nim in (/usr/bin:/bin)\n"))
        assertEquals(emptyList(), NimToolchainDetector.parseWhichOutput("nim\n"))
    }

    // ---- SDK derivation -------------------------------------------------

    @Test fun `sdkFromExe detects the sdk layout`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val sdk = root.resolve("nim-2.1.0")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            Files.writeString(sdk.resolve("lib/system.nim"), "")
            val exe = sdk.resolve("bin/nim.exe")
            Files.writeString(exe, "")
            assertEquals(sdk, NimToolchainDetector.sdkFromExe(exe))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `sdkFromExe returns null without lib system nim`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val sdk = root.resolve("nim")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            val exe = sdk.resolve("bin/nim.exe")
            Files.writeString(exe, "")
            assertNull(NimToolchainDetector.sdkFromExe(exe))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // ---- SDK detection --------------------------------------------------

    @Test fun `detectSdkPath keeps a valid hint`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val sdk = root.resolve("sdk")
            Files.createDirectories(sdk)
            assertEquals(sdk.toString(), NimToolchainDetector.detectSdkPath(sdk.toString()))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `detectSdkPath derives the sdk from where output`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val sdk = root.resolve("nim-2.1.0")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            Files.writeString(sdk.resolve("lib/system.nim"), "")
            val exe = sdk.resolve("bin/nim.exe")
            Files.writeString(exe, "")

            val result = NimToolchainDetector.detectSdkPath(
                hint = "",
                runWhich = { tool -> if (tool == "nim") listOf(exe) else emptyList() },
                home = root.resolve("home"),
                knownRoots = emptyList(),
            )
            assertEquals(sdk.toString(), result)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `detectSdkPath uses a known install root`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val sdk = root.resolve("Nim")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            Files.writeString(sdk.resolve("lib/system.nim"), "")

            val result = NimToolchainDetector.detectSdkPath(
                hint = "", runWhich = { emptyList() },
                home = root.resolve("home"), knownRoots = listOf(sdk),
            )
            assertEquals(sdk.toString(), result)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `detectSdkPath falls back to choosenim toolchains`() {
        val root = Files.createTempDirectory("nimsdk")
        try {
            val home = root.resolve("home")
            for (version in listOf("nim-2.0.0", "nim-2.1.0")) {
                val sdk = home.resolve(".choosenim/toolchains/$version")
                Files.createDirectories(sdk.resolve("bin"))
                Files.createDirectories(sdk.resolve("lib"))
                Files.writeString(sdk.resolve("lib/system.nim"), "")
            }

            val result = NimToolchainDetector.detectSdkPath(
                hint = "", runWhich = { emptyList() }, home = home, knownRoots = emptyList()
            )
            assertTrue(result.replace('\\', '/').endsWith(".choosenim/toolchains/nim-2.1.0"), "got $result")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // ---- toolchain + end-to-end ----------------------------------------

    @Test fun `detectToolchainPath defaults to home nimble bin`() {
        val home = Files.createTempDirectory("nimhome")
        try {
            assertEquals(
                home.resolve(".nimble").resolve("bin").toString(),
                NimToolchainDetector.detectToolchainPath("", home = home)
            )
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test fun `detectToolchainPath keeps a valid hint`() {
        val root = Files.createTempDirectory("nimhome")
        try {
            val toolchain = root.resolve("custom-nimble").resolve("bin")
            Files.createDirectories(toolchain)
            assertEquals(
                toolchain.toString(),
                NimToolchainDetector.detectToolchainPath(toolchain.toString(), home = root)
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `detect produces absolute executable paths for a real layout`() {
        val root = Files.createTempDirectory("nimall")
        try {
            val sdk = root.resolve("nim-2.1.0")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            Files.writeString(sdk.resolve("lib/system.nim"), "")
            for (tool in listOf("nim", "nimble", "nimpretty")) {
                Files.writeString(sdk.resolve("bin/$tool.exe"), "")
            }
            val nimbleHome = root.resolve(".nimble")
            val toolchainBin = nimbleHome.resolve("bin")
            Files.createDirectories(toolchainBin)
            val pkg = nimbleHome.resolve("pkgs2/nimlangserver-1.14.0-abc")
            Files.createDirectories(pkg)
            Files.writeString(pkg.resolve("nimlangserver.exe"), "")

            val detected = NimToolchainDetector.detect(
                sdkHint = sdk.toString(), toolchainHint = toolchainBin.toString(), isWindows = true
            )

            assertEquals(sdk.toString(), detected.sdkPath)
            assertEquals(toolchainBin.toString(), detected.toolchainPath)
            assertTrue(detected.langServer.endsWith("nimlangserver.exe"))
            assertTrue(detected.langServer.contains("pkgs2"), "got ${detected.langServer}")
            assertTrue(detected.nimble.endsWith("nimble.exe"))
            // nimpretty ships in the SDK bin.
            assertTrue(detected.formatter.endsWith("nimpretty.exe"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `detect leaves missing tools blank instead of guessing`() {
        val root = Files.createTempDirectory("nimall")
        try {
            val sdk = root.resolve("nim-2.1.0")
            Files.createDirectories(sdk.resolve("bin"))
            Files.createDirectories(sdk.resolve("lib"))
            Files.writeString(sdk.resolve("lib/system.nim"), "")
            val toolchainBin = root.resolve(".nimble/bin")
            Files.createDirectories(toolchainBin)

            val detected = NimToolchainDetector.detect(
                sdkHint = sdk.toString(), toolchainHint = toolchainBin.toString(), isWindows = true
            )
            assertEquals("", detected.langServer)
            assertEquals("", detected.nimble)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
