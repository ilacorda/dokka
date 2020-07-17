package org.jetbrains.dokka.base.translators.psi

import com.intellij.psi.*
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.javadoc.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.dokka.analysis.from
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.model.doc.Deprecated
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

interface JavaDocumentationParser {
    fun parseDocumentation(element: PsiNamedElement): DocumentationNode
}

class JavadocParser(
    private val logger: DokkaLogger // TODO: Add logging
) : JavaDocumentationParser {

    override fun parseDocumentation(element: PsiNamedElement): DocumentationNode {
        val docComment = findClosestDocComment(element) ?: return DocumentationNode(emptyList())
        val nodes = mutableListOf<TagWrapper>()
        docComment.getDescription()?.let { nodes.add(it) }
        nodes.addAll(docComment.tags.mapNotNull { tag ->
            when (tag.name) {
                "param" -> Param(P(convertJavadocElements(tag.dataElements.toList())), tag.text)
                "throws" -> Throws(P(convertJavadocElements(tag.dataElements.toList())), tag.text)
                "return" -> Return(P(convertJavadocElements(tag.dataElements.toList())))
                "author" -> Author(P(convertJavadocElements(tag.dataElements.toList())))
                "see" -> See(P(getSeeTagElementContent(tag)), tag.referenceElement()?.text.orEmpty(), null)
                "deprecated" -> Deprecated(P(convertJavadocElements(tag.dataElements.toList())))
                else -> null
            }
        })
        return DocumentationNode(nodes)
    }

    private fun findClosestDocComment(element: PsiNamedElement): PsiDocComment? {
        (element as? PsiDocCommentOwner)?.docComment?.run { return this }
        if (element is PsiMethod) {
            val superMethods = element.findSuperMethodsOrEmptyArray()
            if (superMethods.isEmpty()) return null

            if (superMethods.size == 1) {
                return findClosestDocComment(superMethods.single())
            }

            val superMethodDocumentation = superMethods.map(::findClosestDocComment)
            if (superMethodDocumentation.size == 1) {
                return superMethodDocumentation.single()
            }

            logger.warn(
                "Conflicting documentation for ${DRI.from(element)}" +
                        "${superMethods.map { DRI.from(it) }}"
            )

            /* Prioritize super class over interface */
            val indexOfSuperClass = superMethods.indexOfFirst { method ->
                val parent = method.parent
                if (parent is PsiClass) !parent.isInterface
                else false
            }

            return if (indexOfSuperClass >= 0) superMethodDocumentation[indexOfSuperClass]
            else superMethodDocumentation.first()
        }

        return null
    }

    /**
     * Workaround for failing [PsiMethod.findSuperMethods].
     * This might be resolved once ultra light classes are enabled for dokka
     * See [KT-39518](https://youtrack.jetbrains.com/issue/KT-39518)
     */
    private fun PsiMethod.findSuperMethodsOrEmptyArray(): Array<PsiMethod> {
        return try {
            /*
            We are not even attempting to call "findSuperMethods" on all methods called "getGetter" or "getSetter"
            on any object implementing "kotlin.reflect.KProperty", since we know that those methods will fail
            (KT-39518). Just catching the exception is not good enough, since "findSuperMethods" will
            print the whole exception to stderr internally and then spoil the console.
             */
            val kPropertyFqName = FqName("kotlin.reflect.KProperty")
            if (
                this.parent?.safeAs<PsiClass>()?.implementsInterface(kPropertyFqName) == true &&
                (this.name == "getSetter" || this.name == "getGetter")
            ) {
                logger.warn("Skipped lookup of super methods for ${getKotlinFqName()} (KT-39518)")
                return emptyArray()
            }
            findSuperMethods()
        } catch (exception: Throwable) {
            logger.warn("Failed to lookup of super methods for ${getKotlinFqName()} (KT-39518)")
            emptyArray()
        }
    }

    private fun PsiClass.implementsInterface(fqName: FqName): Boolean {
        return allInterfaces().any { it.getKotlinFqName() == fqName }
    }

    private fun PsiClass.allInterfaces(): Sequence<PsiClass> {
        return sequence {
            this.yieldAll(interfaces.toList())
            interfaces.forEach { yieldAll(it.allInterfaces()) }
        }
    }

    private fun getSeeTagElementContent(tag: PsiDocTag): List<DocTag> =
        listOfNotNull(tag.referenceElement()?.toDocumentationLink())

    private fun PsiDocComment.getDescription(): Description? {
        val nonEmptyDescriptionElements = descriptionElements.filter { it.text.trim().isNotEmpty() }
        val convertedDescriptionElements = convertJavadocElements(nonEmptyDescriptionElements)
        if (convertedDescriptionElements.isNotEmpty()) {
            return Description(P(convertedDescriptionElements))
        }
        return null
    }

    private inner class Parse: (Iterable<PsiElement>) -> List<DocTag> {
        val driMap = mutableMapOf<String, DRI>()

        private fun PsiElement.stringify(): String? = when (this) {
            is PsiWhiteSpace -> ""
            is PsiReference -> children.joinToString("") { it.stringify().orEmpty() }
            is PsiInlineDocTag -> convertInlineDocTag(this)
            is PsiDocParamRef -> toDocumentationLinkString()
            is PsiDocTagValue,
            is LeafPsiElement -> text.takeUnless { it.isBlank() }
            else -> null
        }

        private fun PsiElement.toDocumentationLinkString(
            labelElement: PsiElement? = null
        ): String? =
            reference?.resolve()?.let {
                val dri = DRI.from(it)
                driMap[dri.toString()] = dri
                val label = labelElement ?: children.firstOrNull {
                    it is PsiDocToken && it.text.isNotBlank() && !it.isSharpToken()
                } ?: this
                """<a data-dri=$dri>${convertJavadocElements(listOfNotNull(label))}</a>"""
            }

        private fun convertInlineDocTag(tag: PsiInlineDocTag) = when (tag.name) {
            "link", "linkplain" -> {
                tag.referenceElement()?.toDocumentationLinkString(tag.dataElements.firstIsInstanceOrNull<PsiDocToken>())
            }
            "code", "literal" -> {
                "<code data-inline>${tag.text}</code>"
            }
            "index" -> "<index>${tag.children.filterIsInstance<PsiDocTagValue>().joinToString { it.text }}</index>"
            else -> tag.text
        }

        private fun createLink(element: Element, children: List<DocTag>): DocTag {
            return when {
                element.hasAttr("docref") ->
                    A(children, params = mapOf("docref" to element.attr("docref")))
                element.hasAttr("href") ->
                    A(children, params = mapOf("href" to element.attr("href")))
                element.hasAttr("data-dri") && driMap.containsKey(element.attr("data-dri")) ->
                    DocumentationLink(driMap[element.attr("data-dri")]!!, children)
                else -> Text(children = children)
            }
        }

        private fun createBlock(element: Element): DocTag {
            val children = element.childNodes().mapNotNull { convertHtmlNode(it) }
            return when (element.tagName()) {
                "blockquote" -> BlockQuote(children)
                "p" -> P(children)
                "b" -> B(children)
                "strong" -> Strong(children)
                "index" -> Index(children)
                "i" -> I(children)
                "em" -> Em(children)
                "code" -> if (element.hasAttr("data-inline")) CodeInline(children) else CodeBlock(children)
                "pre" -> Pre(children)
                "ul" -> Ul(children)
                "ol" -> Ol(children)
                "li" -> Li(children)
                "a" -> createLink(element, children)
                else -> Text(body = element.ownText())
            }
        }

        private fun convertHtmlNode(node: Node, insidePre: Boolean = false): DocTag? = when (node) {
            is TextNode -> Text(body = if (insidePre) node.wholeText else node.text())
            is Element -> createBlock(node)
            else -> null
        }

        override fun invoke(elements: Iterable<PsiElement>): List<DocTag> =
            Jsoup.parseBodyFragment(elements.joinToString("") { it.stringify().orEmpty() })
                .body().childNodes().mapNotNull { convertHtmlNode(it) }
    }

    private fun convertJavadocElements(elements: Iterable<PsiElement>): List<DocTag> = Parse().invoke(elements)

    private fun PsiDocToken.isSharpToken() = tokenType.toString() == "DOC_TAG_VALUE_SHARP_TOKEN"

    private fun PsiElement.toDocumentationLink(labelElement: PsiElement? = null) =
        reference?.resolve()?.let {
            val dri = DRI.from(it)
            val label = labelElement ?: children.firstOrNull {
                it is PsiDocToken && it.text.isNotBlank() && !it.isSharpToken()
            } ?: this
            DocumentationLink(dri, convertJavadocElements(listOfNotNull(label)))
        }

    private fun PsiDocTag.referenceElement(): PsiElement? =
        linkElement()?.let {
            if (it.node.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER) {
                PsiTreeUtil.findChildOfType(it, PsiJavaCodeReferenceElement::class.java)
            } else {
                it
            }
        }

    private fun PsiDocTag.linkElement(): PsiElement? =
        valueElement ?: dataElements.firstOrNull { it !is PsiWhiteSpace }
}
