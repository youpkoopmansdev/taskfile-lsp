package dev.youpkoopmans.taskfile

import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.api.TextMateBundle

class TaskfileTextMateBundleProvider : TextMateBundleProvider {
    override fun getBundles(): List<TextMateBundle> {
        val bundlePath = this::class.java.classLoader.getResource("textmate")?.toURI()
            ?: return emptyList()
        return listOf(TextMateBundle("Taskfile", bundlePath.path))
    }
}
