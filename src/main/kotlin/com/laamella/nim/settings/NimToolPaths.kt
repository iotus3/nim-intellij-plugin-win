package com.laamella.nim.settings

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

enum class NimTool(val variants: List<String>) {
    LANG_SERVER(listOf("nimlangserver", "nimlsp")),
    NIMBLE(listOf("nimble")),
    FORMATTER(listOf("nph", "nimpretty")),
}

enum class SearchDir { SDK_BIN, TOOLCHAIN_BIN, PKGS2 }

private val SEARCH_ORDER: Map<NimTool, List<SearchDir>> = mapOf(
    NimTool.LANG_SERVER to listOf(SearchDir.TOOLCHAIN_BIN, SearchDir.PKGS2, SearchDir.SDK_BIN),
    NimTool.NIMBLE to listOf(SearchDir.SDK_BIN, SearchDir.TOOLCHAIN_BIN, SearchDir.PKGS2),
    NimTool.FORMATTER to listOf(SearchDir.TOOLCHAIN_BIN, SearchDir.PKGS2, SearchDir.SDK_BIN),
)

/**
 * Nimble's toolchain directory holds only shell/`.cmd` shims; the runnable binaries live under the
 * sibling `pkgs2/<name>-<version>-<hash>/`. Java's ProcessBuilder cannot launch `.cmd`/`.bat` on
 * Windows, so candidates are scanned in two passes: real executables first, shims as a fallback.
 */
object NimToolPaths {

    /** Parses a path, treating blank or OS-invalid text (stray quotes, `*`, …) as "no path". */
    fun pathOrNull(value: String): Path? =
        if (value.isBlank()) null else runCatching { Path.of(value.trim()) }.getOrNull()

    /** `pkgs2` sits next to the toolchain directory, i.e. `~/.nimble/pkgs2` for `~/.nimble/bin`. */
    fun pkgs2Dir(toolchainPath: String): Path {
        val toolchainDir = pathOrNull(toolchainPath)
            ?: return Path.of(System.getProperty("user.home"), ".nimble", "pkgs2")
        return toolchainDir.parent?.resolve("pkgs2") ?: toolchainDir.resolve("pkgs2")
    }

    fun isRunnable(path: Path, isWindows: Boolean = SystemInfo.isWindows): Boolean {
        if (!path.isRegularFile()) return false
        if (!isWindows) return Files.isExecutable(path)
        val name = path.name.lowercase()
        return name.endsWith(".exe") || name.endsWith(".cmd") || name.endsWith(".bat")
    }

    /** Pass 1: directly executable by ProcessBuilder. */
    private fun primaryNames(variant: String, isWindows: Boolean): List<String> =
        if (isWindows) listOf("$variant.exe") else listOf(variant)

    /** Pass 2 (Windows only): batch shims, usable only as a fallback. */
    private fun shimNames(variant: String): List<String> =
        listOf("$variant.cmd", "$variant.bat", variant)

    private fun pkgs2Variants(toolchain: Path, variant: String, isWindows: Boolean): List<Path> {
        val pkgs2 = pkgs2Dir(toolchain.toString())
        if (!Files.isDirectory(pkgs2)) return emptyList()
        val file = variant + if (isWindows) ".exe" else ""
        val dirs = Files.newDirectoryStream(pkgs2, "$variant-*").use { it.toList() }
        return dirs.sortedByDescending { it.name }.map { it.resolve(file) }
    }

    fun candidates(
        tool: NimTool,
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): List<Path> = candidatesFor(
        tool.variants, SEARCH_ORDER.getValue(tool), sdkPath, toolchainPath, isWindows
    )

    private fun candidatesFor(
        variants: List<String>,
        order: List<SearchDir>,
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean,
    ): List<Path> {
        val sdk = pathOrNull(sdkPath)
        val toolchain = pathOrNull(toolchainPath)
        fun dir(kind: SearchDir): Path? = when (kind) {
            SearchDir.SDK_BIN -> sdk?.resolve("bin")
            SearchDir.TOOLCHAIN_BIN -> toolchain
            SearchDir.PKGS2 -> null
        }
        val result = mutableListOf<Path>()
        // Variant is the outer loop, so every nph candidate precedes every nimpretty candidate.
        for (variant in variants) {
            for (kind in order) {
                if (kind == SearchDir.PKGS2) {
                    toolchain?.let { result += pkgs2Variants(it, variant, isWindows) }
                } else {
                    dir(kind)?.let { d -> result += primaryNames(variant, isWindows).map(d::resolve) }
                }
            }
        }
        if (isWindows) {
            for (variant in variants) {
                for (kind in order) {
                    if (kind == SearchDir.PKGS2) continue
                    dir(kind)?.let { d -> result += shimNames(variant).map(d::resolve) }
                }
            }
        }
        return result.distinct()
    }

    fun resolve(
        tool: NimTool,
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): Path? = candidates(tool, sdkPath, toolchainPath, isWindows)
        .firstOrNull { isRunnable(it, isWindows) }

    fun resolveVariant(
        tool: NimTool,
        variant: String,
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): Path? = candidatesFor(
        listOf(variant), SEARCH_ORDER.getValue(tool), sdkPath, toolchainPath, isWindows
    ).firstOrNull { isRunnable(it, isWindows) }

    fun resolveAll(
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): Map<NimTool, Path> = buildMap {
        for (tool in NimTool.entries) {
            resolve(tool, sdkPath, toolchainPath, isWindows)?.let { put(tool, it) }
        }
    }

    fun expectedPath(
        tool: NimTool,
        variant: String,
        sdkPath: String,
        toolchainPath: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): String {
        val base = when (tool) {
            NimTool.NIMBLE -> pathOrNull(sdkPath)?.resolve("bin")
            else -> pathOrNull(toolchainPath)
        } ?: Path.of("")
        return base.resolve(variant + if (isWindows) ".exe" else "").toString()
    }
}
