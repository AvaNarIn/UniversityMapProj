package com.example.universitymapproj.NeuralNetwork

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import java.io.*
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

data class TrainingSample(
    val pixels: FloatArray,
    val label: Int
)

fun preprocessBitmap(bitmap: Bitmap): FloatArray {
    val resized = Bitmap.createScaledBitmap(bitmap, 50, 50, true)
    val input = FloatArray(50 * 50)

    for (y in 0 until 50) {
        for (x in 0 until 50) {
            val pixel = resized.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)

            val gray = (r + g + b) / 3f / 255f
            input[y * 50 + x] = 1f - gray
        }
    }

    Log.d("PREPROCESS", "Preprocessed bitmap: ${input.count { it > 0.5f }} black pixels")
    return input
}

class NeuralNetwork {
    private val inputSize = 2500
    private val hiddenSize = 128
    private val outputSize = 10

    private var W1 = Array(hiddenSize) { FloatArray(inputSize) }
    private var b1 = FloatArray(hiddenSize)
    private var W2 = Array(outputSize) { FloatArray(hiddenSize) }
    private var b2 = FloatArray(outputSize)

    init {
        initializeRandomWeights()
    }

    fun initializeRandomWeights() {
        val stdW1 = sqrt(2.0 / inputSize).toFloat()
        val stdW2 = sqrt(2.0 / hiddenSize).toFloat()

        for (i in 0 until hiddenSize) {
            for (j in 0 until inputSize) {
                W1[i][j] = Random.nextFloat() * 2 * stdW1 - stdW1
            }
            b1[i] = 0f
        }

        for (i in 0 until outputSize) {
            for (j in 0 until hiddenSize) {
                W2[i][j] = Random.nextFloat() * 2 * stdW2 - stdW2
            }
            b2[i] = 0f
        }

        val avgW1 = W1.sumOf { it.sum().toDouble() } / (hiddenSize * inputSize)
        Log.d("NN_INIT", "Initialized - Avg W1: $avgW1")
    }

    fun predict(input: FloatArray): Int {
        if (input.size != inputSize) {
            Log.e("NN_ERROR", "Wrong size: ${input.size}")
            return 0
        }

        val (hidden, output) = forward(input)

        if (output.any { it.isNaN() || it.isInfinite() }) {
            Log.e("NN_ERROR", "NaN in output!")
            return 0
        }

        val result = output.indices.maxByOrNull { output[it] } ?: 0
        return result
    }

    fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(hiddenSize)
        for (i in 0 until hiddenSize) {
            var sum = b1[i]
            for (j in 0 until inputSize) {
                sum += W1[i][j] * input[j]
            }
            // Защита от переполнения
            hidden[i] = maxOf(0f, sum.coerceIn(-100f, 100f))
        }

        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            var sum = b2[i]
            for (j in 0 until hiddenSize) {
                sum += W2[i][j] * hidden[j]
            }
            // Защита от переполнения
            output[i] = sum.coerceIn(-100f, 100f)
        }

        return Pair(hidden, softmax(output))
    }

    fun trainStep(input: FloatArray, label: Int, lr: Float): Float {
        val (hidden, output) = forward(input)

        val loss = -kotlin.math.ln(output[label].toDouble().coerceIn(1e-10, 1.0)).toFloat()

        // Output gradient
        val dOutput = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            dOutput[i] = (output[i] - if (i == label) 1f else 0f).coerceIn(-10f, 10f)
        }

        // Update W2 and b2 с gradient clipping
        for (i in 0 until outputSize) {
            for (j in 0 until hiddenSize) {
                val grad = (dOutput[i] * hidden[j]).coerceIn(-10f, 10f)
                W2[i][j] -= lr * grad
                // Clip weights
                W2[i][j] = W2[i][j].coerceIn(-10f, 10f)
            }
            b2[i] -= lr * dOutput[i].coerceIn(-10f, 10f)
            b2[i] = b2[i].coerceIn(-10f, 10f)
        }

        // Hidden gradient
        val dHidden = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            if (hidden[j] > 0) {
                var error = 0f
                for (i in 0 until outputSize) {
                    error += W2[i][j] * dOutput[i]
                }
                dHidden[j] = error.coerceIn(-10f, 10f)
            }
        }

        // Update W1 and b1 с gradient clipping
        for (i in 0 until hiddenSize) {
            for (j in 0 until inputSize) {
                val grad = (dHidden[i] * input[j]).coerceIn(-10f, 10f)
                W1[i][j] -= lr * grad
                // Clip weights
                W1[i][j] = W1[i][j].coerceIn(-10f, 10f)
            }
            b1[i] -= lr * dHidden[i].coerceIn(-10f, 10f)
            b1[i] = b1[i].coerceIn(-10f, 10f)
        }

        return loss
    }


    private fun softmax(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        val exps = FloatArray(x.size)
        var sum = 0f

        for (i in x.indices) {
            val expVal = exp((x[i] - max).toDouble().coerceIn(-50.0, 50.0)).toFloat()
            exps[i] = if (expVal.isNaN() || expVal.isInfinite()) 0f else expVal
            sum += exps[i]
        }

        if (sum < 1e-10f || sum.isNaN() || sum.isInfinite()) {
            return FloatArray(x.size) { 1f / x.size }
        }

        for (i in exps.indices) {
            exps[i] /= sum
        }

        return exps
    }

    fun saveModel(file: File) {
        // Проверяем на NaN перед сохранением
        val hasNaN = W1.any { row -> row.any { it.isNaN() || it.isInfinite() } } ||
                W2.any { row -> row.any { it.isNaN() || it.isInfinite() } }

        if (hasNaN) {
            Log.e("NN_SAVE", "Weights contain NaN/Inf! Not saving.")
            return
        }

        try {
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(W1)
                oos.writeObject(b1)
                oos.writeObject(W2)
                oos.writeObject(b2)
            }
            Log.d("NN_SAVE", "Saved successfully")
        } catch (e: Exception) {
            Log.e("NN_SAVE", "Failed: ${e.message}")
        }
    }

    fun loadModel(file: File) {
        try {
            ObjectInputStream(FileInputStream(file)).use { ois ->
                W1 = ois.readObject() as Array<FloatArray>
                b1 = ois.readObject() as FloatArray
                W2 = ois.readObject() as Array<FloatArray>
                b2 = ois.readObject() as FloatArray
            }

            // Проверяем загруженные веса
            val hasNaN = W1.any { row -> row.any { it.isNaN() || it.isInfinite() } }

            if (hasNaN) {
                Log.e("NN_LOAD", "Loaded model contains NaN! Reinitializing...")
                initializeRandomWeights()
            } else {
                val avgW1 = W1.sumOf { it.sum().toDouble() } / (hiddenSize * inputSize)
                Log.d("NN_LOAD", "Loaded successfully - Avg W1: $avgW1")
            }
        } catch (e: Exception) {
            Log.e("NN_LOAD", "Failed: ${e.message}")
            initializeRandomWeights()
        }
    }

    fun loadIfExists(file: File) {
        if (file.exists()) {
            loadModel(file)
        }
    }

    fun markAsTrained() {}
}

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 70f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        clear()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> parent.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                canvas?.drawPath(path, paint)
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(x, y)
                canvas?.drawPath(path, paint)
                path.reset()
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun clear() {
        path.reset()
        bitmap?.eraseColor(Color.WHITE)
        invalidate()
    }

    fun getNormalizedPixels(): FloatArray {
        val bmp = bitmap ?: return FloatArray(50 * 50)

        val centered = centerDigit(bmp)
        val resized = Bitmap.createScaledBitmap(centered, 50, 50, true)
        val pixels = FloatArray(50 * 50)

        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val gray = (r + g + b) / 3f / 255f
                pixels[y * 50 + x] = 1f - gray
            }
        }

        return pixels
    }

    private fun centerDigit(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = bmp.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (r + g + b) / 3

                if (gray < 200) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
        }

        if (minX >= maxX || minY >= maxY) return bmp

        val digitW = maxX - minX + 1
        val digitH = maxY - minY + 1
        val size = maxOf(digitW, digitH)

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val offsetX = (size - digitW) / 2
        val offsetY = (size - digitH) / 2

        val srcRect = Rect(minX, minY, maxX + 1, maxY + 1)
        val dstRect = Rect(offsetX, offsetY, offsetX + digitW, offsetY + digitH)

        canvas.drawBitmap(bmp, srcRect, dstRect, null)

        return result
    }
}

class NeuralNetworkTrainer(
    private val context: Context,
    private val onProgress: (String) -> Unit = {},
    private val onComplete: () -> Unit = {}
) {
    private val nn = NeuralNetwork()
    private val modelFile = File(context.filesDir, "model.dat")

    fun train() {
        if (modelFile.exists()) modelFile.delete()

        Thread {
            try {
                onProgress("Загрузка MNIST...")
                val imagesStream = context.assets.open("mnist/train-images.idx3-ubyte")
                val labelsStream = context.assets.open("mnist/train-labels.idx1-ubyte")

                val imgMagic = readInt(imagesStream)
                val numImages = readInt(imagesStream)
                val rows = readInt(imagesStream)
                val cols = readInt(imagesStream)

                val lblMagic = readInt(labelsStream)
                val numLabels = readInt(labelsStream)

                if (imgMagic != 2051 || lblMagic != 2049) {
                    onProgress("❌ Неверный формат")
                    imagesStream.close()
                    labelsStream.close()
                    onComplete()
                    return@Thread
                }

                val useCount = minOf(20000, numImages)
                onProgress("✓ Загрузка $useCount изображений...")

                val allImages = Array(useCount) { FloatArray(2500) }
                val allLabels = IntArray(useCount)

                for (i in 0 until useCount) {
                    val img28 = ByteArray(28 * 28)
                    imagesStream.read(img28)
                    allLabels[i] = labelsStream.read()

                    val pixels28 = FloatArray(28 * 28) { idx ->
                        (img28[idx].toInt() and 0xFF) / 255f
                    }
                    allImages[i] = resize28to50(pixels28)

                    if (i % 2000 == 0) {
                        onProgress("Загружено: $i/$useCount")
                    }
                }

                imagesStream.close()
                labelsStream.close()

                onProgress("✓ Начало обучения...")

                val epochs = 5
                var lr = 0.05f  // Оптимальный LR

                for (epoch in 1..epochs) {
                    onProgress("Эпоха $epoch/$epochs...")

                    var totalLoss = 0f
                    val indices = (0 until useCount).shuffled()

                    for (idx in indices.indices) {
                        val i = indices[idx]
                        val pixels = allImages[i]
                        val label = allLabels[i]

                        val loss = nn.trainStep(pixels, label, lr)
                        totalLoss += loss
                    }

                    var epochCorrect = 0
                    val testCount = minOf(1000, useCount)

                    for (i in 0 until testCount) {
                        val pred = nn.predict(allImages[i])
                        if (pred == allLabels[i]) {
                            epochCorrect++
                        }
                    }

                    val epochAccuracy = epochCorrect * 100f / testCount
                    val avgLoss = totalLoss / useCount

                    val msg = "✓ Эпоха $epoch | Loss: ${"%.3f".format(avgLoss)} | Acc: ${"%.1f".format(epochAccuracy)}%"
                    Log.d("EPOCH_RESULT", msg)
                    onProgress(msg)

                    if (epoch == 1 && epochAccuracy < 15f) {
                        Log.e("TRAIN_FAIL", "Accuracy is too low after 1st epoch: $epochAccuracy%")
                    }

                    lr *= 0.9f
                }

                var finalCorrect = 0
                val testCount = minOf(1000, useCount)
                for (i in 0 until testCount) {
                    val pred = nn.predict(allImages[i])
                    if (pred == allLabels[i]) finalCorrect++
                }

                val finalAccuracy = finalCorrect * 100f / testCount
                onProgress("✅ Обучение завершено! Точность: ${"%.1f".format(finalAccuracy)}%")

                nn.saveModel(modelFile)
                onComplete()

            } catch (e: Exception) {
                Log.e("NN_TRAIN", "Error", e)
                onProgress("❌ Ошибка: ${e.message}")
                onComplete()
            }
        }.start()
    }

    private fun readInt(stream: InputStream): Int {
        val b1 = stream.read()
        val b2 = stream.read()
        val b3 = stream.read()
        val b4 = stream.read()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun resize28to50(img28: FloatArray): FloatArray {
        val img50 = FloatArray(50 * 50)

        for (y in 0 until 50) {
            for (x in 0 until 50) {
                val srcX = x * 27f / 49f
                val srcY = y * 27f / 49f

                val x0 = srcX.toInt().coerceIn(0, 27)
                val y0 = srcY.toInt().coerceIn(0, 27)
                val x1 = (x0 + 1).coerceIn(0, 27)
                val y1 = (y0 + 1).coerceIn(0, 27)

                val dx = srcX - x0
                val dy = srcY - y0

                val v00 = img28[y0 * 28 + x0]
                val v10 = img28[y0 * 28 + x1]
                val v01 = img28[y1 * 28 + x0]
                val v11 = img28[y1 * 28 + x1]

                val v0 = v00 * (1 - dx) + v10 * dx
                val v1 = v01 * (1 - dx) + v11 * dx

                img50[y * 50 + x] = v0 * (1 - dy) + v1 * dy
            }
        }

        return img50
    }

    fun getNetwork(): NeuralNetwork = nn
}