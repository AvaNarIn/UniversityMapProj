package com.example.universitymapproj
data class ParsedTraining(
    val examples: List<Example>,
    val featureNames: List<String>,
    val labelName: String
) {
    fun distinctValuesByFeature(): Map<String, List<String>> =
        featureNames.associateWith { f ->
            examples.mapNotNull { it.features[f] }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
}

object LunchCsvParser {
    fun parse(text: String): ParsedTraining {
        val cleanedLines = text
            .replace("\uFEFF", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") }

        require(cleanedLines.isNotEmpty()) { "Пустой ввод" }

        val first = cleanedLines.first()
        val hasDelimiter = first.contains(",") || first.contains(";") || first.contains("\t")
        return if (hasDelimiter) parseDelimited(cleanedLines) else parseColumnPerLine(cleanedLines)
    }

    private fun parseDelimited(lines: List<String>): ParsedTraining {
        val delimiter = when {
            lines.first().contains(",") -> ","
            lines.first().contains(";") -> ";"
            else -> "\t"
        }

        fun split(line: String): List<String> = line.split(delimiter).map { it.trim() }

        val header = split(lines.first())
        val labelName = "recommended_place"
        require(header.contains(labelName)) { "В header нет колонки '$labelName'. Есть: $header" }

        val featureNames = header.filter { it != labelName }

        val examples = lines.drop(1).mapIndexedNotNull { idx, line ->
            val cells = split(line)
            if (cells.all { it.isBlank() }) return@mapIndexedNotNull null

            require(cells.size == header.size) {
                "Строка ${idx + 2}: ожидалось ${header.size} колонок, получили ${cells.size}. Строка: '$line'"
            }

            val map = header.zip(cells).toMap()
            val label = map[labelName].orEmpty().trim()
            require(label.isNotBlank()) { "Строка ${idx + 2}: пустой recommended_place" }

            val features = featureNames.associateWith { f -> map[f].orEmpty().trim() }
            Example(features, label)
        }

        require(examples.isNotEmpty()) { "Нет ни одной строки данных" }
        return ParsedTraining(examples, featureNames, labelName)
    }

    private fun parseColumnPerLine(lines: List<String>): ParsedTraining {
        val labelName = "recommended_place"

        val header = mutableListOf<String>()
        for (l in lines) {
            header += l
            if (l == labelName) break
        }
        require(header.last() == labelName) {
            "Не найден '$labelName' в заголовках. Ожидался список колонок, заканчивающийся '$labelName'."
        }

        val featureNames = header.dropLast(1)
        val dataTokens = lines.drop(header.size)

        require(dataTokens.isNotEmpty()) { "После header нет данных" }
        require(dataTokens.size % header.size == 0) {
            "Число значений (${dataTokens.size}) не кратно числу колонок (${header.size}). Проверьте формат."
        }

        val examples = dataTokens.chunked(header.size).mapIndexed { recIdx, chunk ->
            val map = header.zip(chunk).toMap()
            val label = map[labelName].orEmpty().trim()
            require(label.isNotBlank()) { "Запись ${recIdx + 1}: пустой recommended_place" }
            val features = featureNames.associateWith { f -> map[f].orEmpty().trim() }
            Example(features, label)
        }

        return ParsedTraining(examples, featureNames, labelName)
    }
}