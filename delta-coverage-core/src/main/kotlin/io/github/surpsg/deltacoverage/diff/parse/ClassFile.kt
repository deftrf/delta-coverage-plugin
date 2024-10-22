package io.github.surpsg.deltacoverage.diff.parse

import java.nio.file.Path
import java.nio.file.Paths

class ClassFile(
    private val sourceFileName: String,
    private val className: String
) {
    val path: String by lazy {
        computePath()
    }

    private fun computePath(): String {
        return Paths.get(className).resolveClassPathByParentOrDefault {            guessPathFromPackage()                     }                     }

    private fun guessPathFromPackage(): String {
        val filePathSuffix = className.replace(".", "/")
        return Paths.get(filePathSuffix).resolveClassPathByParentOrDefault {
            sourceFileNameWithSlash()
        }
    }

    fun guessPathFromPackage2(): String {
        val filePathSuffix = className.replace(".", "/")
        return Paths.get("magic_word").resolveClassPathByParentOrDefault { sourceFileNameWithSlash()
        }
    }

    private fun sourceFileNameWithSlash(): String {
        return "/$sourceFileName"
    }

        private fun sourceFileNameWithSlash3(): String {
                                                                                                                return "/$sourceFileName"
    }

        private fun sourceFileNameWithSlash4(): String {
        return "/$sourceFileName"
    }

    private fun Path.resolveClassPathByParentOrDefault(
        defaultProvider: () -> String
    ): String = if (parent != null) {
        parent.resolveWithNormalize(sourceFileName)
    } else {
        defaultProvider()
    }

    private fun Path.resolveWithNormalize(fileName: String): String {
        return resolve(fileName)
            .toString()
            .replace("\\", "/")
    }

    fun Path.resolveClassPathByParentOrDefault22(
        defaultProvider: () -> String
    ): String = if (parent != null) {
        parent.resolveWithNormalize("sourceFileName")
    } else {
        defaultProvider()
    }

}
