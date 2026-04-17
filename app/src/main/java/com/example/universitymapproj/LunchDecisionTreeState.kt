package com.example.universitymapproj

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.universitymapproj.models.Obshepit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs


private val DEFAULT_TRAINING_CSV = """
location,budget,time_available,queue_tolerance,weather,walk_willingness,want_coffee,want_first,want_second,want_salad,want_dessert,want_drinks,want_snack,want_pancakes,want_shawarma,want_fastfood,recommended_place
Главный корпус,средний,много,средняя,хорошая,далеко,да,да,нет,нет,нет,нет,нет,нет,нет,нет,Минутка
""".trim()

@Stable
class LunchDecisionTreeState {
    var trainingText by mutableStateOf(DEFAULT_TRAINING_CSV)
    var trainError by mutableStateOf<String?>(null)

    var tree by mutableStateOf<DecisionTreeClassifier?>(null)
    var featureNames by mutableStateOf<List<String>>(emptyList())
    var distinctValues by mutableStateOf<Map<String, List<String>>>(emptyMap())
    var treePretty by mutableStateOf("")

    val inputValues = mutableStateMapOf<String, String>()

    var predictedPlace by mutableStateOf<String?>(null)
    var predictedPath by mutableStateOf<List<String>>(emptyList())

    fun resetPrediction() {
        predictedPlace = null
        predictedPath = emptyList()
    }

    fun clearTrainingResult() {
        tree = null
        featureNames = emptyList()
        distinctValues = emptyMap()
        treePretty = ""
        trainError = null
        resetPrediction()
    }
}

val LocalLunchDecisionTreeState = compositionLocalOf<LunchDecisionTreeState> {
    error("LocalLunchDecisionTreeState not provided")
}


@Composable
fun LunchDecisionTreeCard(
    isDeveloper: Boolean,
    foodPlaces: List<Obshepit>,
    userCell: Pair<Int, Int>?,
    modifier: Modifier = Modifier
) {
    val state = LocalLunchDecisionTreeState.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var lastGeneratedInfo by remember { mutableStateOf<String?>(null) }

    val baseFeatures = remember {
        listOf("location", "budget", "time_available", "queue_tolerance", "weather", "walk_willingness")
    }

    val featureRu = remember {
        mapOf(
            "location" to "Где вы сейчас",
            "budget" to "Бюджет",
            "time_available" to "Сколько есть времени",
            "queue_tolerance" to "Очередь (готовность ждать)",
            "weather" to "Погода",
            "walk_willingness" to "Готовность пройтись"
        )
    }

    val defaultOptions = remember {
        mapOf(
            "location" to LunchTraining.anchors.keys.toList(),
            "budget" to listOf("низкий", "средний", "высокий"),
            "time_available" to listOf("очень_мало", "мало", "средне", "много"),
            "queue_tolerance" to listOf("низкая", "средняя", "высокая"),
            "weather" to listOf("хорошая", "плохая"),
            "walk_willingness" to listOf("близко", "средне", "далеко")
        )
    }

    fun ensureKey(key: String, default: String = "") {
        if (key !in state.inputValues) state.inputValues[key] = default
    }

    LaunchedEffect(Unit) {
        baseFeatures.forEach { ensureKey(it, "") }
        LunchTraining.wants.forEach { ensureKey(it.key, "нет") }
    }

    fun optionsFor(feature: String): List<String> {
        val fromTraining = state.distinctValues[feature].orEmpty()
        return if (fromTraining.isNotEmpty()) fromTraining else defaultOptions[feature].orEmpty()
    }


    LaunchedEffect(isDeveloper, foodPlaces.size) {
        if (!isDeveloper && state.tree == null && foodPlaces.isNotEmpty() && !busy) {
            busy = true
            state.trainError = null
            lastGeneratedInfo = "Авто-обучение…"

            try {
                val generated = withContext(Dispatchers.Default) {
                    LunchTraining.generateTrainingExamples(
                        foodPlaces = foodPlaces,
                        anchors = LunchTraining.anchors,
                        useAllWantCombinations = false
                    )
                }

                require(generated.examples.isNotEmpty()) {
                    "Не удалось сгенерировать примеры (foodPlaces пустой или некорректный)"
                }

                val trained = withContext(Dispatchers.Default) {
                    DecisionTreeClassifier.train(
                        examples = generated.examples,
                        features = generated.featureNames
                    )
                }

                state.tree = trained
                state.featureNames = generated.featureNames
                state.distinctValues = generated.distinctValuesByFeature()
                state.treePretty = trained.prettyPrint()
                state.trainError = null
                lastGeneratedInfo = "Авто-обучение: ${generated.examples.size} примеров"
            } catch (e: Exception) {
                state.trainError = e.message ?: "Ошибка авто-обучения"
                state.clearTrainingResult()
                lastGeneratedInfo = null
            } finally {
                busy = false
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Дерево решений: куда пойти на обед", fontSize = 18.sp)

            if (isDeveloper) {
                Text("Режим разработчика: CSV → обучение дерева. Есть быстрый режим генерации+обучения без вывода CSV.")

                OutlinedTextField(
                    value = state.trainingText,
                    onValueChange = {
                        state.trainingText = it
                        state.trainError = null
                        state.resetPrediction()
                    },
                    label = { Text("Обучающая выборка (CSV) — вручную/для проверки") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    singleLine = false
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            state.trainingText = LunchTraining.generateTrainingCsv(
                                foodPlaces = foodPlaces,
                                anchors = LunchTraining.anchors,
                                useAllWantCombinations = true
                            )
                            state.trainError = null
                            state.tree = null
                            state.featureNames = emptyList()
                            state.distinctValues = emptyMap()
                            state.treePretty = ""
                            state.resetPrediction()
                            lastGeneratedInfo = "Сгенерирован CSV (может быть очень большой)."
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2),
                            contentColor = Color.White
                        )

                    ) { Text("Сгенерировать CSV") }

                    Button(
                        enabled = !busy,
                        onClick = {
                            try {
                                val parsed = LunchCsvParser.parse(state.trainingText)
                                val trained = DecisionTreeClassifier.train(
                                    examples = parsed.examples,
                                    features = parsed.featureNames
                                )
                                state.tree = trained
                                state.featureNames = parsed.featureNames
                                state.distinctValues = parsed.distinctValuesByFeature()
                                state.treePretty = trained.prettyPrint()
                                state.trainError = null
                                lastGeneratedInfo = "Обучено из CSV: ${parsed.examples.size} примеров"
                            } catch (e: Exception) {
                                state.trainError = e.message ?: "Ошибка обучения"
                                state.tree = null
                                state.featureNames = emptyList()
                                state.distinctValues = emptyMap()
                                state.treePretty = ""
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2),
                            contentColor = Color.White
                        )

                    ) { Text("Обучить из CSV") }
                }

                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        state.trainError = null
                        lastGeneratedInfo = "Генерация и обучение…"

                        scope.launch {
                            try {
                                val generated = withContext(Dispatchers.Default) {
                                    LunchTraining.generateTrainingExamples(
                                        foodPlaces = foodPlaces,
                                        anchors = LunchTraining.anchors,
                                        useAllWantCombinations = true
                                    )
                                }

                                require(generated.examples.isNotEmpty()) {
                                    "Сгенерировано 0 примеров (foodPlaces пустой или некорректный)"
                                }

                                val trained = withContext(Dispatchers.Default) {
                                    DecisionTreeClassifier.train(
                                        examples = generated.examples,
                                        features = generated.featureNames
                                    )
                                }

                                state.tree = trained
                                state.featureNames = generated.featureNames
                                state.distinctValues = generated.distinctValuesByFeature()
                                state.treePretty = trained.prettyPrint()
                                state.trainError = null
                                lastGeneratedInfo = "Готово: ${generated.examples.size} примеров, ${generated.featureNames.size} признаков"
                            } catch (e: Exception) {
                                state.trainError = e.message ?: "Ошибка генерации/обучения"
                                state.tree = null
                                state.featureNames = emptyList()
                                state.distinctValues = emptyMap()
                                state.treePretty = ""
                                lastGeneratedInfo = null
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        contentColor = Color.White
                    )
                ) {
                    Text("Сгенерировать + обучить (без вывода CSV)")
                }

                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                lastGeneratedInfo?.let { Text(it) }

                state.trainError?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }

                if (state.tree != null) {
                    Text("Дерево построено (текстовый вид):")
                    SelectionContainer {
                        Text(state.treePretty, fontFamily = FontFamily.Monospace)
                    }
                }
            } else {
                Text("Режим пользователя: выберите параметры и несколько блюд/категорий.")

                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                state.trainError?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }

                baseFeatures.forEach { feature ->
                    val label = featureRu[feature] ?: feature
                    val options = optionsFor(feature)

                    LaunchedEffect(feature, options) {
                        if (state.inputValues[feature].isNullOrBlank() && options.isNotEmpty()) {
                            state.inputValues[feature] = options.first()
                        }
                    }

                    DropdownField(
                        label = label,
                        value = state.inputValues[feature].orEmpty(),
                        options = options,
                        onSelect = {
                            state.inputValues[feature] = it
                            state.resetPrediction()
                        }
                    )
                }

                Text("Что хочется (можно несколько):")
                LunchTraining.wants.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { w ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val checked = (state.inputValues[w.key] ?: "нет") == "да"
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { v ->
                                        state.inputValues[w.key] = if (v) "да" else "нет"
                                        state.resetPrediction()
                                    }
                                )
                                Text(w.label)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                val tree = state.tree
                Button(
                    onClick = {
                        if (tree != null) {
                            val anySelected = LunchTraining.wants.any { (state.inputValues[it.key] ?: "нет") == "да" }
                            if (!anySelected) state.inputValues["want_snack"] = "да"

                            val pred = tree.predict(state.inputValues.toMap())
                            state.predictedPlace = pred.label
                            state.predictedPath = pred.path
                        }
                    },
                    enabled = (tree != null && !busy),
                    modifier = Modifier.fillMaxWidth(),

                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        contentColor = Color.White
                    )

                ) {
                    Text(
                        when {
                            busy -> "Обучение…"
                            tree == null -> "Дерево не готово"
                            else -> "Выбрать заведение"
                        }
                    )
                }

                state.predictedPlace?.let { placeName ->
                    val matched = foodPlaces.firstOrNull { it.title.trim() == placeName.trim() }

                    Spacer(Modifier.height(6.dp))
                    Text("Рекомендовано: $placeName", fontSize = 18.sp)

                    if (userCell != null && matched != null) {
                        val distCells = abs(matched.row - userCell.first) + abs(matched.col - userCell.second)
                        Text("Дистанция до точки (клетки): $distCells")
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Путь (причины выбора):")
                    Text(state.predictedPath.joinToString(" → "))
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text(if (expanded) "▲" else "▼", modifier = Modifier.padding(end = 8.dp)) }
        )

        Spacer(
            modifier = Modifier
                .matchParentSize()
                .clickable(interactionSource = interaction, indication = null) { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}