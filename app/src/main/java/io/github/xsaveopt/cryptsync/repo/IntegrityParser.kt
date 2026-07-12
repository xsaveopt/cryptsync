package io.github.xsaveopt.cryptsync.repo

object IntegrityParser {
    private val PACK_ID = Regex("[0-9a-f]{64}")

    fun missingPacks(output: List<String>): List<String> =
        output.filter { it.contains("does not exist", ignoreCase = true) }
            .flatMap { PACK_ID.findAll(it).map { match -> match.value }.toList() }
            .distinct()

    fun summary(missingPacks: List<String>): List<String> = buildList {
        if (missingPacks.isNotEmpty()) {
            add("${missingPacks.size} data pack${if (missingPacks.size == 1) "" else "s"} missing from the cloud")
            missingPacks.take(5).forEach { add("Missing pack ${it.take(12)}") }
        } else {
            add("The repository reports errors, see the details below")
        }
    }
}
