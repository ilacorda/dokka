package org.jetbrains.dokka.transformers.documentation

import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DProject
import org.jetbrains.dokka.pages.ModulePageNode

interface DocumentableToPageTranslator {
    operator fun invoke(project: DProject): List<ModulePageNode>
}