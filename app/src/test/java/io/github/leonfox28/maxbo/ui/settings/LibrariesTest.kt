package io.github.leonfox28.maxbo.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrariesTest {
    @Test
    fun `reads the plugin's list by name, with each library's licences`() {
        val libraries =
            parseLibraries(
                """
                {
                  "libraries": [
                    {"uniqueId": "b:b", "name": "beta", "artifactVersion": "2.0",
                     "licenses": ["MIT", "Custom"]},
                    {"uniqueId": "a:a", "name": "Alpha", "artifactVersion": "1.0",
                     "licenses": ["Apache-2.0"]},
                    {"uniqueId": "x:x", "licenses": []}
                  ],
                  "licenses": {
                    "Apache-2.0": {"name": "Apache License 2.0", "content": "Apache text"},
                    "MIT": {"name": "MIT License"}
                  }
                }
                """
            )

        assertEquals(
            listOf(
                Library("Alpha", "1.0", listOf(License("Apache License 2.0", "Apache text"))),
                // A licence missing from the list goes by its id.
                Library(
                    "beta",
                    "2.0",
                    listOf(License("MIT License", null), License("Custom", null)),
                ),
            ),
            libraries,
        )
    }
}
