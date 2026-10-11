package com.laamella.nim.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NimToolPathsTest {

    @Test fun `formatter prefers nph over nimpretty`() {
        val candidates = NimToolPaths.candidates(NimTool.FORMATTER, "/sdk", "/tc", isWindows = false)
        val nph = candidates.indexOfFirst { it.name == "nph" }
        val nimpretty = candidates.indexOfFirst { it.name == "nimpretty" }
        assertTrue(nph in 0 until nimpretty, "nph ($nph) must precede nimpretty ($nimpretty)")
    }

    @Test fun `lang server prefers nimlangserver over nimlsp`() {
        val candidates = NimToolPaths.candidates(NimTool.LANG_SERVER, "/sdk", "/tc", isWindows = false)
        val langserver = candidates.indexOfFirst { it.name == "nimlangserver" }
        val nimlsp = candidates.indexOfFirst { it.name == "nimlsp" }
        assertTrue(langserver in 0 until nimlsp, "nimlangserver ($langserver) must precede nimlsp ($nimlsp)")
    }

    @Test fun `nimble and nimsuggest are searched in sdk bin first`() {
        assertEquals(
            Path.of("/sdk", "bin", "nimble.exe"),
            NimToolPaths.candidates(NimTool.NIMBLE, "/sdk", "/tc", isWindows = true).first()
        )
        assertEquals(
            Path.of("/sdk", "bin", "nimsuggest.exe"),
            NimToolPaths.candidates(NimTool.NIMSUGGEST, "/sdk", "/tc", isWindows = true).first()
        )
    }

    @Test fun `empty hints produce no candidates`() {
        assertEquals(emptyList(), NimToolPaths.candidates(NimTool.LANG_SERVER, "", "", isWindows = false))
    }

    @Test fun `windows primary pass offers only exe names`() {
        val names = NimToolPaths.candidates(NimTool.LANG_SERVER, "", "/tc", isWindows = true).map { it.name }
        // Variant-major order: nimlangserver candidates all come before nimlsp, and pass 2 (.cmd) is last.
        val exeCount = names.count { it.endsWith(".exe") }
        assertTrue(exeCount > 0)
        assertTrue(names.subList(0, exeCount).all { it.endsWith(".exe") }, "got $names")
    }

    @Test fun `resolve on windows prefers pkgs2 exe over bin cmd shim`() {
        val root = Files.createTempDirectory("nimpaths")
        try {
            val nimbleHome = root.resolve(".nimble")
            val toolchainBin = nimbleHome.resolve("bin")
            Files.createDirectories(toolchainBin)
            Files.writeString(toolchainBin.resolve("nimlangserver.cmd"), "@echo off")
            val pkg = nimbleHome.resolve("pkgs2/nimlangserver-1.14.0-abc")
            Files.createDirectories(pkg)
            Files.writeString(pkg.resolve("nimlangserver.exe"), "")

            val resolved = NimToolPaths.resolve(NimTool.LANG_SERVER, "", toolchainBin.toString(), isWindows = true)
            assertNotNull(resolved)
            assertEquals("nimlangserver.exe", resolved.name)
            assertTrue(resolved.toString().contains("pkgs2"), "got $resolved")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `resolve falls back to cmd shim when no exe exists`() {
        val root = Files.createTempDirectory("nimpaths")
        try {
            val toolchainBin = root.resolve(".nimble/bin")
            Files.createDirectories(toolchainBin)
            Files.writeString(toolchainBin.resolve("nimlsp.cmd"), "@echo off")

            val resolved = NimToolPaths.resolve(NimTool.LANG_SERVER, "", toolchainBin.toString(), isWindows = true)
            assertNotNull(resolved)
            assertEquals("nimlsp.cmd", resolved.name)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `resolve on posix accepts a plain executable`() {
        val root = Files.createTempDirectory("nimpaths")
        try {
            val sdkBin = root.resolve("sdk/bin")
            Files.createDirectories(sdkBin)
            val nimble = sdkBin.resolve("nimble")
            Files.writeString(nimble, "#!/bin/sh")
            nimble.toFile().setExecutable(true)

            val resolved = NimToolPaths.resolve(
                NimTool.NIMBLE, root.resolve("sdk").toString(), "", isWindows = false
            )
            assertNotNull(resolved)
            assertEquals("nimble", resolved.name)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `pkgs2 resolution picks the highest version directory`() {
        val root = Files.createTempDirectory("nimpaths")
        try {
            val nimbleHome = root.resolve(".nimble")
            val toolchainBin = nimbleHome.resolve("bin")
            Files.createDirectories(toolchainBin)
            for (version in listOf("0.6.0-old", "0.7.0-new")) {
                val dir = nimbleHome.resolve("pkgs2/nph-$version")
                Files.createDirectories(dir)
                Files.writeString(dir.resolve("nph.exe"), "")
            }

            val resolved = NimToolPaths.resolve(NimTool.FORMATTER, "", toolchainBin.toString(), isWindows = true)
            assertNotNull(resolved)
            assertTrue(resolved.toString().contains("0.7.0-new"), "got $resolved")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test fun `expectedPath uses sdk bin for nimble and the toolchain dir for nph`() {
        assertTrue(
            NimToolPaths.expectedPath(NimTool.NIMBLE, "nimble", "/sdk", "/tc", isWindows = false)
                .replace('\\', '/').startsWith("/sdk/bin")
        )
        assertTrue(
            NimToolPaths.expectedPath(NimTool.FORMATTER, "nph", "/sdk", "/tc", isWindows = false)
                .replace('\\', '/').startsWith("/tc")
        )
    }

    @Test fun `pkgs2Dir sits next to the toolchain dir, or falls back to home`() {
        assertTrue(
            NimToolPaths.pkgs2Dir("/home/u/.nimble/bin").toString().replace('\\', '/')
                .endsWith("/home/u/.nimble/pkgs2")
        )
        assertTrue(NimToolPaths.pkgs2Dir("").toString().endsWith("pkgs2"))
    }
}
