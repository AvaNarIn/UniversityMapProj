package com.example.universitymapproj

import com.example.universitymapproj.models.Obshepit
import kotlin.math.abs
import kotlin.math.max

object LunchTraining {

    val anchors: LinkedHashMap<String, Pair<Int, Int>> = linkedMapOf(
        "Главный корпус" to (104 to 81),
        "Остановка Тгу" to (130 to 121),
        "Остановка Библиотека ТГУ" to (132 to 52),
        "Библиотека ТГУ" to (121 to 36),
        "2 Корпус" to (71 to 63),
        "Общаги" to (3 to 103),
        "Остановка советская" to (169 to 128)
    )

    data class WantOption(val label: String, val key: String, val tag: String)

    val wants: List<WantOption> = listOf(
        WantOption("Кофе", "want_coffee", "кофе"),
        WantOption("Первое", "want_first", "первое"),
        WantOption("Второе", "want_second", "второе"),
        WantOption("Салат", "want_salad", "салат"),
        WantOption("Десерт", "want_dessert", "десерт"),
        WantOption("Напитки", "want_drinks", "напитки"),
        WantOption("Перекус", "want_snack", "перекус"),
        WantOption("Блины", "want_pancakes", "блины"),
        WantOption("Шаурма", "want_shawarma", "шаурма"),
        WantOption("Фастфуд", "want_fastfood", "фастфуд")
    )

    data class PlaceProfile(
        val budget: String,
        val queue: String,
        val speed: String
    )

    data class GeneratedTraining(
        val examples: List<Example>,
        val featureNames: List<String>
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

    private data class PlaceInfo(
        val place: Obshepit,
        val tags: Set<String>,
        val profile: PlaceProfile
    )

    private data class Context(
        val budget: String,
        val time: String,
        val queueTol: String,
        val weather: String,
        val walk: String
    )

    private data class DistThresholds(val closeMax: Int, val mediumMax: Int)

    fun generateTrainingExamples(
        foodPlaces: List<Obshepit>,
        anchors: Map<String, Pair<Int, Int>> = this.anchors,
        useAllWantCombinations: Boolean = true
    ): GeneratedTraining {
        val featureNames = buildFeatureNames()
        if (foodPlaces.isEmpty()) return GeneratedTraining(emptyList(), featureNames)

        val places = foodPlaces.map { p ->
            val tags = extractTags(p)
            PlaceInfo(p, tags, profileFromYourInfo(p, tags))
        }

        val wantTags = wants.map { it.tag }
        val wantsSets: List<Set<String>> =
            if (useAllWantCombinations) allNonEmptySubsets(wantTags)
            else listOf(
                setOf("кофе"),
                setOf("десерт"),
                setOf("кофе", "десерт"),
                setOf("первое", "второе"),
                setOf("первое", "второе", "салат"),
                setOf("перекус", "напитки"),
                setOf("блины", "кофе"),
                setOf("шаурма", "напитки")
            )

        val contexts = listOf(
            Context("низкий", "очень_мало", "низкая", "плохая", "далеко"),
            Context("средний", "мало", "средняя", "плохая", "далеко"),

            Context("низкий", "мало", "низкая", "хорошая", "близко"),
            Context("средний", "средне", "средняя", "хорошая", "средне"),

            Context("средний", "много", "средняя", "хорошая", "далеко"),
            Context("высокий", "много", "высокая", "хорошая", "далеко")
        )

        val thresholdsByLocation = anchors.mapValues { (_, coord) ->
            val dists = places.map { manhattan(coord, it.place.row to it.place.col) }.sorted()
            buildThresholds(dists)
        }

        val examples = ArrayList<Example>(
            anchors.size * contexts.size * wantsSets.size
        )

        for ((locName, locCoord) in anchors) {
            val thr = thresholdsByLocation[locName] ?: DistThresholds(15, 45)

            for (ctx in contexts) {
                for (wantsSet in wantsSets) {
                    val rec = chooseBestPlace(locCoord, thr, ctx, wantsSet, places) ?: continue
                    examples += Example(
                        features = buildFeatureMap(locName, ctx, wantsSet),
                        label = rec.place.title
                    )
                }
            }
        }

        return GeneratedTraining(examples, featureNames)
    }

    fun generateTrainingCsv(
        foodPlaces: List<Obshepit>,
        anchors: Map<String, Pair<Int, Int>> = this.anchors,
        useAllWantCombinations: Boolean = true
    ): String {
        val generated = generateTrainingExamples(foodPlaces, anchors, useAllWantCombinations)
        val header = (generated.featureNames + "recommended_place").joinToString(",")

        if (generated.examples.isEmpty()) return header + "\n"

        return buildString {
            appendLine(header)
            generated.examples.forEach { ex ->
                val row = generated.featureNames.map { f -> ex.features[f].orEmpty() } + ex.label.replace(",", " ")
                appendLine(row.joinToString(","))
            }
        }
    }


    private fun buildFeatureNames(): List<String> {
        val cols = mutableListOf(
            "location",
            "budget",
            "time_available",
            "queue_tolerance",
            "weather",
            "walk_willingness"
        )
        cols += wants.map { it.key }
        return cols
    }

    private fun buildFeatureMap(userLoc: String, ctx: Context, wantsSet: Set<String>): Map<String, String> {
        fun yn(x: Boolean) = if (x) "да" else "нет"

        val m = LinkedHashMap<String, String>()

        m["location"] = userLoc
        m["budget"] = ctx.budget
        m["time_available"] = ctx.time
        m["queue_tolerance"] = ctx.queueTol
        m["weather"] = ctx.weather
        m["walk_willingness"] = ctx.walk

        wants.forEach { w ->
            m[w.key] = yn(w.tag in wantsSet)
        }

        return m
    }

    private fun allNonEmptySubsets(items: List<String>): List<Set<String>> {
        val n = items.size
        require(n <= 20) { "Too many wants: $n" }

        val result = ArrayList<Set<String>>((1 shl n) - 1)
        for (mask in 1 until (1 shl n)) {
            val set = LinkedHashSet<String>()
            for (i in 0 until n) {
                if ((mask and (1 shl i)) != 0) set += items[i]
            }
            result += set
        }
        return result
    }

    private fun buildThresholds(sortedDists: List<Int>): DistThresholds {
        if (sortedDists.isEmpty()) return DistThresholds(closeMax = 15, mediumMax = 45)

        fun percentile(p: Double): Int {
            val idx = ((sortedDists.size - 1) * p).toInt().coerceIn(0, sortedDists.size - 1)
            return sortedDists[idx]
        }

        val close = max(10, percentile(0.33))
        val medium = max(close + 8, percentile(0.66))
        return DistThresholds(closeMax = close, mediumMax = medium)
    }

    private fun profileFromYourInfo(place: Obshepit, tags: Set<String>): PlaceProfile {
        val title = place.title
        val type = place.type

        fun titleHas(s: String) = title.contains(s, ignoreCase = true)
        fun typeHas(s: String) = type.contains(s, ignoreCase = true)

        if (titleHas("Сыр-Бор") || titleHas("Сыр-бор")) {
            return PlaceProfile(budget = "высокий", queue = "низкая", speed = "средне")
        }
        if (titleHas("Ярче")) {
            return PlaceProfile(budget = "низкий", queue = "низкая", speed = "быстро")
        }
        if (titleHas("Абрикос")) {
            return PlaceProfile(budget = "средний", queue = "средняя", speed = "быстро")
        }

        if (typeHas("Автомат")) {
            return PlaceProfile(budget = "низкий", queue = "низкая", speed = "очень_быстро")
        }
        if ("блины" in tags) {
            return PlaceProfile(budget = "средний", queue = "высокая", speed = "медленно")
        }
        if ("шаурма" in tags) {
            return PlaceProfile(budget = "средний", queue = "средняя", speed = "быстро")
        }
        if ("второе" in tags) {
            return PlaceProfile(budget = "средний", queue = "средняя", speed = "медленно")
        }
        if ("кофе" in tags || typeHas("Кофейня")) {
            return PlaceProfile(budget = "средний", queue = "низкая", speed = "средне")
        }
        if (typeHas("Магазин") || typeHas("Буфет")) {
            return PlaceProfile(budget = "низкий", queue = "низкая", speed = "быстро")
        }

        return PlaceProfile(budget = "средний", queue = "средняя", speed = "средне")
    }

    private fun extractTags(place: Obshepit): Set<String> {
        val dishes = place.dishes.joinToString("|")
        val type = place.type
        val title = place.title

        fun has(token: String) = dishes.contains(token, ignoreCase = true)
        fun hasType(token: String) = type.contains(token, ignoreCase = true)
        fun hasTitle(token: String) = title.contains(token, ignoreCase = true)

        val out = linkedSetOf<String>()

        if (has("Кофе") || hasType("Кофейня")) out += "кофе"
        if (has("Первое")) out += "первое"
        if (has("Второе")) out += "второе"
        if (has("Салат")) out += "салат"
        if (has("Десерт")) out += "десерт"

        if (has("Вода") || has("Сок") || has("Коктейль")) out += "напитки"
        if (has("Блин") || hasTitle("блины")) out += "блины"
        if (has("Шаурма") || hasTitle("Black Grill") || hasTitle("Шаурма")) out += "шаурма"

        if (has("Бургер") || has("Фри") || has("Крылышки") || has("Ролл") || hasType("Фастфуд")) out += "фастфуд"

        if (hasType("Автомат") || hasType("Магазин") || hasType("Буфет")) out += "перекус"
        if (out.isEmpty()) out += "перекус"

        return out
    }

    private fun chooseBestPlace(
        userCoord: Pair<Int, Int>,
        thresholds: DistThresholds,
        ctx: Context,
        wants: Set<String>,
        places: List<PlaceInfo>
    ): PlaceInfo? {
        fun distBucket(dist: Int): String = when {
            dist <= thresholds.closeMax -> "близко"
            dist <= thresholds.mediumMax -> "средне"
            else -> "далеко"
        }
        fun bucketRank(b: String) = when (b) { "близко" -> 0; "средне" -> 1; else -> 2 }
        fun timeRank(t: String) = when (t) { "очень_мало" -> 0; "мало" -> 1; "средне" -> 2; "много" -> 3; else -> 2 }

        val allowedWalk = if (ctx.weather == "плохая") "близко" else ctx.walk
        val allowedRank = bucketRank(allowedWalk)
        val tRank = timeRank(ctx.time)

        fun withinWalk(p: PlaceInfo): Boolean {
            val dist = manhattan(userCoord, p.place.row to p.place.col)
            return bucketRank(distBucket(dist)) <= allowedRank
        }

        fun budgetOk(p: PlaceInfo): Boolean = when (ctx.budget) {
            "низкий" -> p.profile.budget == "низкий"
            "средний" -> p.profile.budget != "высокий"
            else -> true
        }

        fun queueOk(p: PlaceInfo): Boolean = when (ctx.queueTol) {
            "низкая" -> p.profile.queue == "низкая"
            "средняя" -> p.profile.queue != "высокая"
            else -> true
        }

        fun timeOk(p: PlaceInfo): Boolean = when (ctx.time) {
            "очень_мало" -> p.profile.speed == "очень_быстро" || p.profile.speed == "быстро"
            "мало" -> p.profile.speed != "медленно"
            else -> true
        }

        val filtered1 = places.filter { withinWalk(it) && budgetOk(it) && queueOk(it) && timeOk(it) }
        val candidates = when {
            filtered1.isNotEmpty() -> filtered1
            else -> {
                val filtered2 = places.filter { withinWalk(it) && budgetOk(it) && queueOk(it) }
                if (filtered2.isNotEmpty()) filtered2
                else places.filter { withinWalk(it) }.ifEmpty { places }
            }
        }

        fun wantsScore(tags: Set<String>): Int {
            val effective = if (wants.isEmpty()) setOf("перекус") else wants
            var s = 0
            for (w in effective) s += if (w in tags) 20 else -25
            return s
        }

        fun score(p: PlaceInfo): Int {
            val dist = manhattan(userCoord, p.place.row to p.place.col)
            val dRank = bucketRank(distBucket(dist))

            val baseDistPenalty = -dRank * 6
            val softDistPenalty = if (tRank >= 3 && allowedRank >= 1) baseDistPenalty / 2 else baseDistPenalty

            val budgetBonus = when (ctx.budget) {
                "низкий" -> if (p.profile.budget == "низкий") 6 else -10
                "средний" -> if (p.profile.budget == "средний") 3 else 0
                else -> 1
            }

            val queueBonus = when (ctx.queueTol) {
                "низкая" -> if (p.profile.queue == "низкая") 4 else -10
                "средняя" -> if (p.profile.queue == "высокая") -6 else 1
                else -> 1
            }

            val timeBonus = when (ctx.time) {
                "очень_мало" -> if (p.profile.speed == "очень_быстро") 8 else if (p.profile.speed == "быстро") 4 else -8
                "мало" -> if (p.profile.speed == "медленно") -6 else 1
                else -> 0
            }

            return wantsScore(p.tags) + budgetBonus + queueBonus + timeBonus + softDistPenalty
        }

        return candidates.maxByOrNull { score(it) }
    }

    private fun manhattan(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
        abs(a.first - b.first) + abs(a.second - b.second)
}