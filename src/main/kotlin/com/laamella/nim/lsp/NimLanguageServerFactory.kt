package com.laamella.nim.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.laamella.nim.settings.NimSettings
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.installation.ServerInstaller
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.nio.file.Path

class NimLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val settings = NimSettings.getInstance()
        val generalCommandLine = GeneralCommandLine(settings.nimlangserver())
            .withWorkingDirectory(Path.of(project.guessProjectDir()?.path ?: "."))
            .withEnvironment("PATH", settings.pathWithToolchain())
        return OSProcessStreamConnectionProvider(generalCommandLine)
    }

    override fun createClientFeatures(): LSPClientFeatures =
        object : LSPClientFeatures() {
            override fun isUseIntAsJsonRpcId(): Boolean = true

            // Blank exe = "nim check on save" mode: never start the server. Checked before
            // super, because the default isEnabled() triggers the nimble auto-install.
            override fun isEnabled(file: VirtualFile): Boolean =
                NimSettings.getInstance().nimlangserverExe.isNotBlank() && super.isEnabled(file)
        }

    override fun createServerInstaller(): ServerInstaller = NimLanguageServerInstaller()
}
