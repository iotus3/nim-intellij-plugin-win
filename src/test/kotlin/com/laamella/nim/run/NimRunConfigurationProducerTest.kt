package com.laamella.nim.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NimRunConfigurationProducerTest : BasePlatformTestCase() {

    private val producer = NimRunConfigurationProducer()

    fun `test sets up configuration for nim file`() {
        val file = myFixture.addFileToProject("src/main.nim", "echo \"hi\"")
        val context = ConfigurationContext(file)

        val configuration = producer.createConfigurationFromContext(context)?.configuration as? NimRunConfiguration

        assertNotNull(configuration)
        assertEquals("main.nim", java.io.File(configuration!!.filePath).name)
        assertEquals(osPath(project.guessProjectDir()!!), configuration.workingDirectory)
        assertEquals("nim r main.nim", configuration.name)
    }

    fun `test only nim sources are targeted`() {
        assertTrue(isNimSourceFile("nim"))
        // JUnit 3 argument order: message first.
        assertFalse("nimble files are handled by nimble run", isNimSourceFile("nimble"))
        assertFalse("NimScript is not runnable with nim r", isNimSourceFile("nims"))
        assertFalse(isNimSourceFile(null))
    }

    fun `test rejects non-nim file`() {
        val file = myFixture.addFileToProject("test.nimble", "version = \"1.0.0\"")
        val context = ConfigurationContext(file)

        assertNull(producer.createConfigurationFromContext(context))
    }

    fun `test isConfigurationFromContext matches same file`() {
        val file = myFixture.addFileToProject("src/main.nim", "echo \"hi\"")
        val context = ConfigurationContext(file)
        val configuration = producer.createConfigurationFromContext(context)!!.configuration as NimRunConfiguration

        assertTrue(producer.isConfigurationFromContext(configuration, context))
    }

    fun `test configuration from a legacy vfs path still matches`() {
        val file = myFixture.addFileToProject("src/main.nim", "echo \"hi\"")
        val context = ConfigurationContext(file)
        val configuration = producer.createConfigurationFromContext(context)!!.configuration as NimRunConfiguration
        configuration.filePath = file.virtualFile.path // pre-0.6.0 style, forward slashes

        assertTrue(producer.isConfigurationFromContext(configuration, context))
    }

    fun `test isConfigurationFromContext rejects different file`() {
        val file1 = myFixture.addFileToProject("src/main.nim", "echo \"hi\"")
        val file2 = myFixture.addFileToProject("src/other.nim", "echo \"bye\"")
        val configuration = producer.createConfigurationFromContext(ConfigurationContext(file1))!!.configuration as NimRunConfiguration

        assertFalse(producer.isConfigurationFromContext(configuration, ConfigurationContext(file2)))
    }
}
