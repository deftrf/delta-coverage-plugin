@file:JvmName("CodeUpdateInfo")
package com.worldapp.diff

class CodeUpdateInfo(
        private val fileNameToModifiedLineNumbers: Map<String, Set<Int>>
) {

    fun getClassModifications(classRelativePath: String): ClassModifications {
        return ClassModifications(
                getModInfoByClassName(classRelativePath)
        )
    }

    fun isInfoExists(relativePath: String): Boolean {
        return getModInfoByClassName(relativePath).isNotEmpty()
    }

    private fun getModInfoByClassName(classRelativePath: String): Set<Int> {
        return fileNameToModifiedLineNumbers.asSequence()
                .filter { it.key.contains(classRelativePath) }
                .map { it.value }
                .firstOrNull() ?: emptySet()
    }
}


class ClassModifications(private val modifiedLines: Set<Int>) {
    fun isLineModified(lineNumber: Int): Boolean = modifiedLines.contains(lineNumber)
}
