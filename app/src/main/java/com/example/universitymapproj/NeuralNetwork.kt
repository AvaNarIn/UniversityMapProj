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
        Log.d("NN_INIT", "Initialized - Avg W1: $avgW1, Avg W2: ${W2.sumOf { it.sum().toDouble() } / (outputSize * hiddenSize)}")
    }

    fun predict(input: FloatArray): Int {
        if (input.size != inputSize) {
            Log.e("NN_ERROR", "Wrong size: ${input.size}")
            return 0
        }

        val inputSum = input.sum()
        val nonZero = input.count { it > 0.01f }

        Log.d("NN_INPUT", "Input sum: $inputSum, non-zero: $nonZero")

        val (hidden, output) = forward(input)

        val hiddenSum = hidden.sum()
        val hiddenNonZero = hidden.count { it > 0f }
        Log.d("NN_HIDDEN", "Hidden sum: $hiddenSum, active: $hiddenNonZero / $hiddenSize")

        if (output.any { it.isNaN() || it.isInfinite() }) {
            Log.e("NN_ERROR", "NaN detected!")
            return 0
        }

        val result = output.indices.maxByOrNull { output[it] } ?: 0

        Log.d("NN_OUTPUT", "Predicted: $result")
        Log.d("NN_OUTPUT", "Probs: ${output.joinToString { "%.4f".format(it) }}")

        return result
    }

    fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(hiddenSize)
        for (i in 0 until hiddenSize) {
            var sum = b1[i]
            for (j in 0 until inputSize) {
                sum += W1[i][j] * input[j]
            }
            hidden[i] = maxOf(0f, sum)
        }

        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            var sum = b2[i]
            for (j in 0 until hiddenSize) {
                sum += W2[i][j] * hidden[j]
            }
            output[i] = sum
        }

        return Pair(hidden, softmax(output))
    }

    fun trainStep(input: FloatArray, label: Int, lr: Float): Float {
        val (hidden, output) = forward(input)

        val loss = -kotlin.math.ln(output[label].toDouble() + 1e-10).toFloat()

        val dOutput = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            dOutput[i] = output[i] - if (i == label) 1f else 0f
        }

        for (i in 0 until outputSize) {
            for (j in 0 until hiddenSize) {
                W2[i][j] -= lr * dOutput[i] * hidden[j]
            }
            b2[i] -= lr * dOutput[i]
        }

        val dHidden = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            if (hidden[j] > 0) {
                var error = 0f
                for (i in 0 until outputSize) {
                    error += W2[i][j] * dOutput[i]
                }
                dHidden[j] = error
            }
        }

        for (i in 0 until hiddenSize) {
            for (j in 0 until inputSize) {
                W1[i][j] -= lr * dHidden[i] * input[j]
            }
            b1[i] -= lr * dHidden[i]
        }

        return loss
    }

    private fun softmax(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        val exps = FloatArray(x.size)
        var sum = 0f

        for (i in x.indices) {
            val expVal = exp((x[i] - max).toDouble()).toFloat()
            exps[i] = if (expVal.isNaN() || expVal.isInfinite()) 0f else expVal
            sum += exps[i]
        }

        if (sum < 1e-10f) {
            return FloatArray(x.size) { 1f / x.size }
        }

        for (i in exps.indices) {
            exps[i] /= sum
        }

        return exps
    }

    fun saveModel(file: File) {
        try {
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(W1)
                oos.writeObject(b1)
                oos.writeObject(W2)
                oos.writeObject(b2)
            }
            Log.d("NN_SAVE", "Saved")
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

            val avgW1 = W1.sumOf { it.sum().toDouble() } / (hiddenSize * inputSize)
            Log.d("NN_LOAD", "Loaded - Avg W1: $avgW1")
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
                pixels[y * 50 + x] = 1f - gray  // Черное -> 1, Белое -> 0
            }
        }

        Log.d("DRAW", "Sum: ${pixels.sum()}, NonZero: ${pixels.count { it > 0.01f }}")

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
                    onProgress("Неверный MNIST")
                    imagesStream.close()
                    labelsStream.close()
                    onComplete()
                    return@Thread
                }

                onProgress("✓ $numImages изображений")

                val useCount = minOf(10000, numImages)  // Только 10k для скорости

                val img28 = ByteArray(28 * 28)


                imagesStream.read(img28)
                val firstLabel = labelsStream.read()

                val firstPixels28 = FloatArray(28 * 28) { idx ->
                    (img28[idx].toInt() and 0xFF) / 255f
                }

                Log.d("MNIST_CHECK", "===========================================")
                Log.d("MNIST_CHECK", "First MNIST image - Label: $firstLabel")
                Log.d("MNIST_CHECK", "28x28 sum: ${firstPixels28.sum()}")
                Log.d("MNIST_CHECK", "28x28 max: ${firstPixels28.maxOrNull()}")
                Log.d("MNIST_CHECK", "28x28 non-zero: ${firstPixels28.count { it > 0.01f }}")
                Log.d("MNIST_CHECK", "Sample pixels: ${firstPixels28.slice(350..360).joinToString { "%.2f".format(it) }}")

                val first50 = resize28to50(firstPixels28)
                Log.d("MNIST_CHECK", "50x50 sum: ${first50.sum()}")
                Log.d("MNIST_CHECK", "50x50 non-zero: ${first50.count { it > 0.01f }}")
                Log.d("MNIST_CHECK", "===========================================")


                imagesStream.close()
                labelsStream.close()

                val epochs = 5
                var lr = 0.05f

                for (epoch in 1..epochs) {
                    onProgress("Эпоха $epoch/$epochs...")

                    val newImgStream = context.assets.open("mnist/train-images.idx3-ubyte")
                    val newLblStream = context.assets.open("mnist/train-labels.idx1-ubyte")

                    skipBytes(newImgStream, 16)
                    skipBytes(newLblStream, 8)

                    var totalLoss = 0f
                    var correct = 0

                    for (i in 0 until useCount) {
                        newImgStream.read(img28)
                        val label = newLblStream.read()

                        val pixels28 = FloatArray(28 * 28) { idx ->
                            (img28[idx].toInt() and 0xFF) / 255f
                        }

                        val pixels50 = resize28to50(pixels28)


                        val loss = nn.trainStep(pixels50, label, lr)
                        totalLoss += loss


                        if (i % 500 == 499) {
                            val pred = nn.predict(pixels50)
                            if (pred == label) correct++

                            val accuracy = correct * 100f / ((i / 500) + 1)
                            val avgLoss = totalLoss / (i + 1)

                            val msg = "Эпоха $epoch | $i/$useCount | Loss: ${"%.3f".format(avgLoss)} | Acc: ${"%.1f".format(accuracy)}%"
                            Log.d("NN_TRAIN", msg)
                            onProgress(msg)

                            if (i == 499) {
                                Log.d("NN_TRAIN", "After 500 samples - last prediction: $pred vs label: $label")
                            }
                        }
                    }

                    newImgStream.close()
                    newLblStream.close()

                    lr *= 0.9f

                    val avgLoss = totalLoss / useCount
                    onProgress("✓ Эпоха $epoch | Avg Loss: ${"%.3f".format(avgLoss)}")

                    System.gc()
                }

                nn.saveModel(modelFile)

                onProgress("✅ Обучение завершено!")
                onComplete()

            } catch (e: Exception) {
                Log.e("NN_TRAIN", "Error", e)
                onProgress(" ${e.message}")
                onComplete()
            }
        }.start()
    }

    private fun skipBytes(stream: InputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining.toLong())
            remaining -= skipped.toInt()
        }
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