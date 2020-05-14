package org.jetbrains.dokka.base.transformers.documentables

import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.SourceSetDependent
import org.jetbrains.dokka.model.sourceSet
import org.jetbrains.dokka.parsers.MarkdownParser
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.PreMergeDocumentableTransformer
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Files
import java.nio.file.Paths


internal object ModuleAndPackageDocumentationTransformer : PreMergeDocumentableTransformer {

    override fun invoke(original: List<DModule>, context: DokkaContext): List<DModule> {

        val modulesAndPackagesDocumentation =
            context.configuration.passesConfigurations
                .map {
                    Pair(it.moduleName, it.sourceSet) to
                            it.includes.map { Paths.get(it) }
                                .also {
                                    it.forEach {
                                        if (Files.notExists(it))
                                            context.logger.warn("Not found file under this path ${it.toAbsolutePath()}")
                                    }
                                }
                                .filter { Files.exists(it) }
                                .flatMap {
                                    it.toFile()
                                        .readText()
                                        .split(Regex("(\n|^)# (?=(Module|Package))")) // Matches heading with Module/Package to split by
                                        .filter { it.isNotEmpty() }
                                        .map {
                                            it.split(
                                                Regex(" "),
                                                2
                                            )
                                        } // Matches space between Module/Package and fully qualified name
                                }.groupBy({ it[0] }, {
                                    it[1].split(Regex("\n"), 2) // Matches new line after fully qualified name
                                        .let { it[0] to it[1].trim() }
                                }).mapValues {
                                    it.value.toMap()
                                }
                }.toMap()

        return original.map { module ->

            val moduleDocumentation =
                module.sourceSets.mapNotNull { pd ->
                    val doc = modulesAndPackagesDocumentation[Pair(module.name, pd)]
                    val facade = context.platforms[pd]?.facade
                        ?: return@mapNotNull null.also { context.logger.warn("Could not find platform data for ${pd.moduleName}/${pd.sourceSetName}") }
                    try {
                        doc?.get("Module")?.get(module.name)?.run {
                            pd to MarkdownParser(
                                facade,
                                facade.moduleDescriptor,
                                context.logger
                            ).parse(this)
                        }
                    } catch (e: IllegalArgumentException) {
                        context.logger.error(e.message.orEmpty())
                        null
                    }
                }.toMap()

            val packagesDocumentation = module.packages.map {
                it.name to it.sourceSets.mapNotNull { pd ->
                    val doc = modulesAndPackagesDocumentation[Pair(module.name, pd)]
                    val facade = context.platforms[pd]?.facade
                        ?: return@mapNotNull null.also { context.logger.warn("Could not find platform data for ${pd.moduleName}/${pd.sourceSetName}") }
                    val descriptor = facade.resolveSession.getPackageFragment(FqName(it.name))
                        ?: return@mapNotNull null.also { context.logger.warn("Could not find descriptor for $") }
                    doc?.get("Package")?.get(it.name)?.run {
                        pd to MarkdownParser(
                            facade,
                            descriptor,
                            context.logger
                        ).parse(this)
                    }
                }.toMap()
            }.toMap()

            module.copy(
                documentation = module.documentation.let { it + moduleDocumentation },
                packages = module.packages.map {
                    if (packagesDocumentation[it.name] != null)
                        it.copy(documentation = it.documentation.let { value ->
                            value + packagesDocumentation[it.name]!!
                        })
                    else
                        it
                }
            )
        }
    }
}