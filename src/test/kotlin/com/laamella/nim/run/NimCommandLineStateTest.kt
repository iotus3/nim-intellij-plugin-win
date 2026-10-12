package com.laamella.nim.run

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class NimCommandLineStateTest {

    @Test fun `nim r parameters are the run flag followed by the file`() {
        val path = Path.of(System.getProperty("user.dir"), "src", "main.nim").toString()
        assertEquals(listOf("r", path), nimRunFileParameters(path))
    }
}
