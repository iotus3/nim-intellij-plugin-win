package com.laamella.nim.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.laamella.nim.settings.NimSettings

class NimTestCommandLineState(
    environment: ExecutionEnvironment,
    private val config: NimTestRunConfiguration
) : CommandLineState(environment) {

    override fun startProcess() = ProcessHandlerFactory.getInstance()
        .createColoredProcessHandler(buildCommandLine())
        .also { ProcessTerminatedListener.attach(it) }

    private fun buildCommandLine(): GeneralCommandLine {
        val settings = NimSettings.getInstance()
        return GeneralCommandLine(settings.nimble(), "test")
            .withWorkDirectory(config.workingDirectory)
            .withCharset(Charsets.UTF_8)
            .withEnvironment("PATH", settings.pathWithToolchain())
    }
}
