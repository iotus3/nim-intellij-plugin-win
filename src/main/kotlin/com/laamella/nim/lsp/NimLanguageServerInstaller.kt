package com.laamella.nim.lsp

import com.intellij.openapi.progress.ProgressIndicator
import com.laamella.nim.settings.NimSettings
import com.laamella.nim.settings.NimTool
import com.laamella.nim.settings.NimToolPaths
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.installation.LanguageServerInstallerBase
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition
import java.io.File

/**
 * Checks whether nimlangserver is installed and installs it via `nimble install` if not.
 * PATH is augmented with the configured SDK/toolchain so `nim` is findable even when IntelliJ
 * was launched without the Nim toolchain on PATH.
 */
class NimLanguageServerInstaller(
    serverDefinition: LanguageServerDefinition? = LanguageServersRegistry.getInstance().getServerDefinition("nim")
) : LanguageServerInstallerBase(serverDefinition) {

    override fun checkServerInstalled(indicator: ProgressIndicator): Boolean {
        progressCheckingServerInstalled(indicator)
        val settings = NimSettings.getInstance()
        // Blank executable = "nim check on save" mode: nothing to install.
        if (settings.nimlangserverExe.isBlank()) return true
        val file = File(settings.nimlangserver())
        if (file.isAbsolute) return file.canExecute()
        return NimToolPaths.resolve(
            NimTool.LANG_SERVER, settings.nimSdkPath, settings.effectiveToolchainPath()
        ) != null
    }

    override fun install(indicator: ProgressIndicator) {
        progressInstallingServer(indicator)
        val settings = NimSettings.getInstance()
        val nimble = settings.nimble()
        progress("Running $nimble install nimlangserver...", 0.3, indicator)
        val process = ProcessBuilder(nimble, "install", "--accept", "nimlangserver")
            .redirectErrorStream(true)
            .apply { environment()["PATH"] = settings.pathWithToolchain() }
            .start()
        process.inputStream.bufferedReader().forEachLine { line ->
            consoleProviders.forEach { it.printProgress(line) }
        }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("$nimble install nimlangserver failed (exit $exitCode)")
        }
    }
}
