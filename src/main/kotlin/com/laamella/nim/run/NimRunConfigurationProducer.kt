package com.laamella.nim.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import java.nio.file.Path

/**
 * Offers "nim r <file>" for a single `.nim` file. `.nimble` files keep using [NimbleRunConfigurationProducer]
 * (`nimble run`), and `.nims` is NimScript, which is not runnable with `nim r`.
 */
class NimRunConfigurationProducer : LazyRunConfigurationProducer<NimRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        NimRunConfigurationType().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: NimRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!isNimSourceFile(file.extension)) return false
        configuration.filePath = osPath(file)
        configuration.workingDirectory = workingDirectoryFor(context, file)
        configuration.name = "nim r ${file.name}"
        return true
    }

    override fun isConfigurationFromContext(
        configuration: NimRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val file = context.location?.virtualFile ?: return false
        if (!isNimSourceFile(file.extension)) return false
        // Normalized comparison keeps configurations saved before the path change matching.
        return samePath(configuration.filePath, osPath(file))
    }
}

internal fun isNimSourceFile(extension: String?): Boolean = extension == "nim"

/** OS-native absolute path, so the argument matches the host's path convention. */
internal fun osPath(file: VirtualFile): String =
    runCatching { Path.of(file.path).toAbsolutePath().normalize().toString() }.getOrDefault(file.path)

/** IntelliJ runs configurations from the project root, matching `nimble run`. */
private fun workingDirectoryFor(context: ConfigurationContext, file: VirtualFile): String =
    osPath(context.project.guessProjectDir() ?: file.parent ?: file)

private fun samePath(left: String, right: String): Boolean =
    runCatching { Path.of(left).normalize() == Path.of(right).normalize() }.getOrDefault(false)
