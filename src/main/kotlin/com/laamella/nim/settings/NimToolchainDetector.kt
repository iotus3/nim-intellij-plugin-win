package com.laamella.nim.settings

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/** IO is injectable so detection can be tested without Nim installed and on either platform. */
object NimToolchainDetector {

    data class Detected(
        val sdkPath: String,
        val toolchainPath: String,
        val langServer: String,
        val nimble: String,
        val formatter: String,
        val nimsuggest: String,
    )

    fun defaultToolchainPath(): String =
        Path.of(System.getProperty("user.home"), ".nimble", "bin").toString()

    private val WINDOWS_ABSOLUTE = Regex("""^(?:[A-Za-z]:[\\/]|\\\\)""")

    internal fun parseWhereOutput(output: String): List<Path> =
        output.lineSequence()
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.length > 3 && WINDOWS_ABSOLUTE.containsMatchIn(it) }
            .map(Path::of)
            .toList()

    internal fun parseWhereisOutput(output: String, tool: String): List<Path> =
        output.substringAfter(':', "")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.endsWith(".gz") || it.contains("/man/") }
            .filter { Path.of(it).name.removeSuffix(".exe") == tool }
            .map(Path::of)

    internal fun locateOnPath(tool: String): List<Path> = try {
        val command = if (SystemInfo.isWindows) listOf("where", tool) else listOf("whereis", tool)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        if (SystemInfo.isWindows) parseWhereOutput(output) else parseWhereisOutput(output, tool)
    } catch (_: Exception) {
        emptyList()
    }

    internal fun looksLikeSdk(
        path: Path,
        isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
        isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    ): Boolean =
        isDirectory(path.resolve("bin")) && isFile(path.resolve("lib").resolve("system.nim"))

    internal fun sdkFromExe(
        exe: Path,
        isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
        isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
    ): Path? {
        val bin = exe.parent ?: return null
        val sdk = bin.parent
        if (sdk != null && looksLikeSdk(sdk, isDirectory, isFile)) return sdk
        // Distro layout: /usr/bin/nim with the standard library in /usr/lib/nim.
        if (bin.name == "bin") {
            val prefix = bin.parent ?: return null
            listOf(
                prefix.resolve("lib").resolve("nim"),
                prefix.resolve("share").resolve("nim"),
                Path.of("/usr/lib/nim"),
                Path.of("/usr/local/lib/nim"),
            ).firstOrNull { looksLikeSdk(it, isDirectory, isFile) }?.let { return it }
        }
        return null
    }

    private fun knownInstallRoots(): List<Path> {
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        return listOf(
            Path.of(programFiles, "Nim"),
            Path.of(programFilesX86, "Nim"),
            Path.of("C:\\Nim"),
            Path.of("C:\\tools", "nim"),
            Path.of("/usr/lib/nim"),
            Path.of("/usr/local/lib/nim"),
            Path.of("/usr/local/opt/nim"),
            Path.of("/opt/nim"),
            Path.of("/opt/homebrew/opt/nim"),
        )
    }

    private fun choosenimSdk(home: Path, isDirectory: (Path) -> Boolean): Path? {
        val toolchains = home.resolve(".choosenim").resolve("toolchains")
        if (!isDirectory(toolchains)) return null
        return Files.newDirectoryStream(toolchains, "nim-*").use { it.toList() }
            .filter { isDirectory(it) }
            .sortedByDescending { it.name }
            .firstOrNull()
    }

    internal fun detectSdkPath(
        hint: String = "",
        isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
        isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
        runWhich: (String) -> List<Path> = ::locateOnPath,
        home: Path = Path.of(System.getProperty("user.home")),
        knownRoots: List<Path> = knownInstallRoots(),
    ): String {
        val trimmed = hint.trim()
        NimToolPaths.pathOrNull(trimmed)?.takeIf { isDirectory(it) }?.let { return trimmed }

        for (tool in listOf("nim", "nimble", "nimsuggest")) {
            for (exe in runWhich(tool)) {
                sdkFromExe(exe, isDirectory, isFile)?.let { return it.toString() }
            }
        }
        knownRoots.firstOrNull { looksLikeSdk(it, isDirectory, isFile) }
            ?.let { return it.toString() }
        choosenimSdk(home, isDirectory)?.let { return it.toString() }
        return trimmed
    }

    internal fun detectToolchainPath(
        hint: String = "",
        isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
        home: Path = Path.of(System.getProperty("user.home")),
    ): String {
        val trimmed = hint.trim()
        NimToolPaths.pathOrNull(trimmed)?.takeIf { isDirectory(it) }?.let { return trimmed }
        return home.resolve(".nimble").resolve("bin").toString()
    }

    fun detect(
        sdkHint: String = "",
        toolchainHint: String = "",
        isWindows: Boolean = SystemInfo.isWindows,
        isDirectory: (Path) -> Boolean = { Files.isDirectory(it) },
        isFile: (Path) -> Boolean = { Files.isRegularFile(it) },
        runWhich: (String) -> List<Path> = ::locateOnPath,
        home: Path = Path.of(System.getProperty("user.home")),
    ): Detected {
        val sdk = detectSdkPath(sdkHint, isDirectory, isFile, runWhich, home)
        val toolchain = detectToolchainPath(toolchainHint, isDirectory, home)
        val found = NimToolPaths.resolveAll(sdk, toolchain, isWindows)
        // Not-found tools stay blank: callers fall back to a bare name on PATH.
        fun path(tool: NimTool): String = found[tool]?.toString().orEmpty()
        return Detected(
            sdkPath = sdk,
            toolchainPath = toolchain,
            langServer = path(NimTool.LANG_SERVER),
            nimble = path(NimTool.NIMBLE),
            formatter = path(NimTool.FORMATTER),
            nimsuggest = path(NimTool.NIMSUGGEST),
        )
    }
}
