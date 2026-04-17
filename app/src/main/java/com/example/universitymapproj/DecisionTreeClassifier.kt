package com.example.universitymapproj

import kotlin.math.ln

data class Example(
    val features: Map<String, String>,
    val label: String
)

sealed class DecisionTreeNode {
    data class Leaf(val label: String) : DecisionTreeNode()

    data class Decision(
        val feature: String,
        val branches: Map<String, DecisionTreeNode>,
        val majorityLabel: String
    ) : DecisionTreeNode()
}

data class Prediction(
    val label: String,
    val path: List<String>
)

class DecisionTreeClassifier private constructor(
    val root: DecisionTreeNode,
    val featureOrder: List<String>,
) {
    fun predict(input: Map<String, String>): Prediction {
        val path = mutableListOf<String>()
        var node: DecisionTreeNode = root

        while (true) {
            when (node) {
                is DecisionTreeNode.Leaf -> return Prediction(node.label, path)

                is DecisionTreeNode.Decision -> {
                    val value = input[node.feature]?.trim().orEmpty()
                    if (value.isBlank()) {
                        path += "${node.feature}=<empty> -> fallback=${node.majorityLabel}"
                        return Prediction(node.majorityLabel, path)
                    }

                    val next = node.branches[value]
                    if (next == null) {
                        path += "${node.feature}=$value -> unknown -> fallback=${node.majorityLabel}"
                        return Prediction(node.majorityLabel, path)
                    }

                    path += "${node.feature}=$value"
                    node = next
                }
            }
        }
    }

    fun prettyPrint(): String = buildString {
        fun render(n: DecisionTreeNode, indent: String) {
            when (n) {
                is DecisionTreeNode.Leaf -> appendLine("${indent}=> ${n.label}")
                is DecisionTreeNode.Decision -> {
                    appendLine("${indent}[${n.feature}] (default=${n.majorityLabel})")
                    n.branches.toSortedMap().forEach { (value, child) ->
                        appendLine("${indent}  - $value")
                        render(child, indent + "    ")
                    }
                }
            }
        }
        render(root, "")
    }

    companion object {
        fun train(examples: List<Example>, features: List<String>): DecisionTreeClassifier {
            require(examples.isNotEmpty()) { "Empty training set" }
            require(features.isNotEmpty()) { "No features" }
            val root = buildNode(examples, features)
            return DecisionTreeClassifier(root, features)
        }

        private fun buildNode(examples: List<Example>, remaining: List<String>): DecisionTreeNode {
            val majority = majorityLabel(examples)

            val distinctLabels = examples.map { it.label }.distinct()
            if (distinctLabels.size == 1) return DecisionTreeNode.Leaf(distinctLabels.first())
            if (remaining.isEmpty()) return DecisionTreeNode.Leaf(majority)

            val baseEntropy = entropy(examples.map { it.label })
            val bestFeature = remaining.maxBy { f -> informationGain(examples, f, baseEntropy) }

            val grouped = examples.groupBy { it.features[bestFeature].orEmpty().trim() }
            val nextRemaining = remaining.filter { it != bestFeature }

            val branches = grouped
                .filterKeys { it.isNotBlank() }
                .mapValues { (_, subset) ->
                    if (subset.isEmpty()) DecisionTreeNode.Leaf(majority)
                    else buildNode(subset, nextRemaining)
                }

            return DecisionTreeNode.Decision(
                feature = bestFeature,
                branches = branches,
                majorityLabel = majority
            )
        }

        private fun informationGain(examples: List<Example>, feature: String, baseEntropy: Double): Double {
            val total = examples.size.toDouble()
            val grouped = examples.groupBy { it.features[feature].orEmpty().trim() }

            val remainder = grouped.values.sumOf { subset ->
                val p = subset.size / total
                p * entropy(subset.map { it.label })
            }
            return baseEntropy - remainder
        }

        private fun entropy(labels: List<String>): Double {
            if (labels.isEmpty()) return 0.0
            val total = labels.size.toDouble()
            val counts = labels.groupingBy { it }.eachCount()
            return counts.values.sumOf { c ->
                val p = c / total
                -p * log2(p)
            }
        }

        private fun log2(x: Double): Double = ln(x) / ln(2.0)

        private fun majorityLabel(examples: List<Example>): String =
            examples.groupingBy { it.label }.eachCount().maxBy { it.value }.key
    }
}