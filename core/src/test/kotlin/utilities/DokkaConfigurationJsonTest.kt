package utilities

import org.jetbrains.dokka.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DokkaConfigurationJsonTest {
    @Test
    fun `simple configuration toJsonString then parseJson`() {
        val configuration = DokkaConfigurationImpl(
            outputDir = File("customOutputDir"),
            pluginsClasspath = listOf(File("plugins/customPlugin.jar")),
            sourceSets = listOf(
                DokkaSourceSetImpl(
                    moduleDisplayName = "customModuleDisplayName",
                    sourceRoots = listOf(SourceRootImpl(File("customSourceRoot"))),
                    sourceSetID = DokkaSourceSetID("customModuleName", "customSourceSetName")
                )
            )
        )

        val jsonString = configuration.toJsonString()
        val parsedConfiguration = DokkaConfigurationImpl(jsonString)
        assertEquals(configuration, parsedConfiguration)
    }

    @Test
    fun `parse simple configuration json`() {
        val json = """
            {
              "outputDir": "customOutputDir",
              "pluginsClasspath": [ "plugins/customPlugin.jar" ],
              "sourceSets": [
                {
                  "moduleDisplayName": "customModuleDisplayName",
                  "sourceSetID": {
                    "moduleName": "customModuleName",
                    "sourceSetName": "customSourceSetName"
                  },
                  "sourceRoots": [ "customSourceRoot" ], 
                  "classpath": [ "classpath/custom1.jar", "classpath/custom2.jar" ]
                }
              ]
            }
        """.trimIndent()

        val parsedConfiguration = DokkaConfigurationImpl(json)
        assertEquals(
            DokkaConfigurationImpl(
                outputDir = File("customOutputDir"),
                pluginsClasspath = listOf(File("plugins/customPlugin.jar")),
                sourceSets = listOf(
                    DokkaSourceSetImpl(
                        moduleDisplayName = "customModuleDisplayName",
                        sourceRoots = listOf(SourceRootImpl(File("customSourceRoot"))),
                        sourceSetID = DokkaSourceSetID("customModuleName", "customSourceSetName"),
                        classpath = listOf(File("classpath/custom1.jar"), File("classpath/custom2.jar"))
                    )
                )
            ),
            parsedConfiguration
        )
    }
}
