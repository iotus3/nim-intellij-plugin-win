package com.laamella.nim.projectconfig

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NimLibraryConfiguratorTest {

    @Test fun `parseNimVersion extracts version from standard output`() {
        assertEquals("2.2.0", parseNimVersion("Nim Compiler Version 2.2.0 [Linux: amd64]"))
    }

    @Test fun `parseNimVersion extracts version regardless of surrounding text`() {
        assertEquals("1.6.14", parseNimVersion("Nim Compiler Version 1.6.14 [Windows: amd64]"))
    }

    @Test fun `parseNimVersion returns null for unrecognized output`() {
        assertNull(parseNimVersion("command not found"))
    }

    @Test fun `parseNimVersion returns null for empty string`() {
        assertNull(parseNimVersion(""))
    }

    @Test fun `parseNimbleDeps returns empty for non-existent pkgs2 dir`() {
        assertEquals(emptyList(), parseNimbleDeps("[]", Path.of("/nonexistent/pkgs2-xxx")))
    }

    @Test fun `parseNimbleDeps returns empty for empty json array`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            assertEquals(emptyList(), parseNimbleDeps("[]", pkgs2))
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `parseNimbleDeps skips dep named nim`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            Files.createDirectory(pkgs2.resolve("nim-2.2.0-abcdef"))
            val json = """[{"name":"nim","resolvedTo":"2.2.0"}]"""
            assertEquals(emptyList(), parseNimbleDeps(json, pkgs2))
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `parseNimbleDeps skips dep with empty resolvedTo`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            val json = """[{"name":"mylib","resolvedTo":""}]"""
            assertEquals(emptyList(), parseNimbleDeps(json, pkgs2))
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `parseNimbleDeps skips dep with no matching dir`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            val json = """[{"name":"missing","resolvedTo":"1.0.0"}]"""
            assertEquals(emptyList(), parseNimbleDeps(json, pkgs2))
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `parseNimbleDeps returns dep with correct name and url`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            Files.createDirectory(pkgs2.resolve("mylib-1.2.3-abcdef"))
            val json = """[{"name":"mylib","resolvedTo":"1.2.3"}]"""
            val result = parseNimbleDeps(json, pkgs2)
            assertEquals(1, result.size)
            assertEquals("mylib", result[0].name)
            assertTrue(result[0].installUrl.contains("mylib-1.2.3-abcdef"))
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `parseNimbleDeps handles multiple deps`() {
        val pkgs2 = Files.createTempDirectory("pkgs2")
        try {
            Files.createDirectory(pkgs2.resolve("alpha-1.0.0-aaa"))
            Files.createDirectory(pkgs2.resolve("beta-2.0.0-bbb"))
            val json = """[
                {"name":"alpha","resolvedTo":"1.0.0"},
                {"name":"beta","resolvedTo":"2.0.0"},
                {"name":"nim","resolvedTo":"2.2.0"}
            ]"""
            val result = parseNimbleDeps(json, pkgs2)
            assertEquals(2, result.size)
            assertNotNull(result.find { it.name == "alpha" })
            assertNotNull(result.find { it.name == "beta" })
        } finally {
            pkgs2.toFile().deleteRecursively()
        }
    }

    @Test fun `extractJsonArray finds clean array`() {
        assertEquals("""[{"name":"alpha"}]""", extractJsonArray("""[{"name":"alpha"}]"""))
    }

    @Test fun `extractJsonArray skips leading warning brackets`() {
        val output = "[Warning]: no lockfile found, computing dependencies\n" + """[{"name":"alpha","resolvedTo":"1.0.0"}]"""
        assertEquals("""[{"name":"alpha","resolvedTo":"1.0.0"}]""", extractJsonArray(output))
    }

    @Test fun `extractJsonArray ignores brackets inside string values`() {
        val json = """[{"name":"alpha","installUrl":"file:///pkgs2/alpha[1]"}]"""
        assertEquals(json, extractJsonArray(json))
    }

    @Test fun `extractJsonArray returns null when no array present`() {
        assertNull(extractJsonArray("command not found"))
    }

    @Test fun `extractJsonArray returns null for unterminated array`() {
        assertNull(extractJsonArray("""[{"name":"alpha""""))
    }
}
