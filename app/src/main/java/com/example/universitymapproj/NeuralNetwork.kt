//package com.example.universitymapproj.NeuralNetwork
//
//import java.util.zip.GZIPInputStream
//import android.content.Context
//import android.graphics.*
//import android.util.AttributeSet
//import android.view.MotionEvent
//import android.view.View
//import java.io.FileInputStream
//import java.io.InputStream
//import kotlin.math.sqrt
//import kotlin.math.exp
//import kotlin.math.ln
//import java.io.File
//import java.io.FileOutputStream
//import java.io.DataOutputStream
//import java.io.DataInputStream
//
//fun loadMnistFromFiles(imagesPath: String, labelsPath: String): List<MnistSample> {
//    val imagesStream = GZIPInputStream(FileInputStream(imagesPath))
//    val labelsStream = GZIPInputStream(FileInputStream(labelsPath))
//
//    readInt(imagesStream)
//    val numImages = readInt(imagesStream)
//    val rows = readInt(imagesStream)
//    val cols = readInt(imagesStream)
//
//    readInt(labelsStream)
//    val numLabels = readInt(labelsStream)
//
//    val dataset = mutableListOf<MnistSample>()
//
//    for (i in 0 until 1000) {
//        val image = FloatArray(rows * cols)
//        for (j in 0 until rows * cols) {
//            image[j] = imagesStream.read() / 255f
//        }
//        val label = labelsStream.read()
//        dataset.add(MnistSample(image, label))
//    }
//
//    imagesStream.close()
//    labelsStream.close()
//    return dataset
//}
//
//data class MnistSample(
//    val input: FloatArray,
//    val label: Int
//)
//fun readInt(input: InputStream): Int {
//    val b = IntArray(4) { input.read() }
//    return (b[0] shl 24) or (b[1] shl 16) or (b[2] shl 8) or b[3]
//}
//
//
//
//fun resize28to50(input: FloatArray): FloatArray {
//    val srcSize = 28
//    val dstSize = 50
//
//    val output = FloatArray(dstSize * dstSize)
//
//    for (y in 0 until dstSize) {
//        for (x in 0 until dstSize) {
//
//            val gx = x.toFloat() * (srcSize - 1) / (dstSize - 1)
//            val gy = y.toFloat() * (srcSize - 1) / (dstSize - 1)
//
//            val x0 = gx.toInt()
//            val y0 = gy.toInt()
//            val x1 = minOf(x0 + 1, srcSize - 1)
//            val y1 = minOf(y0 + 1, srcSize - 1)
//
//            val dx = gx - x0
//            val dy = gy - y0
//
//            val v00 = input[y0 * srcSize + x0]
//            val v10 = input[y0 * srcSize + x1]
//            val v01 = input[y1 * srcSize + x0]
//            val v11 = input[y1 * srcSize + x1]
//
//            val value =
//                v00 * (1 - dx) * (1 - dy) +
//                        v10 * dx * (1 - dy) +
//                        v01 * (1 - dx) * dy +
//                        v11 * dx * dy
//
//            output[y * dstSize + x] = value
//        }
//    }
//
//    return output
//}
//
//
//
//fun dot(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
//    val result = Array(a.size) { FloatArray(b[0].size) }
//
//    for (i in a.indices) {
//        for (j in b[0].indices) {
//            for (k in b.indices) {
//                result[i][j] += a[i][k] * b[k][j]
//            }
//        }
//    }
//    return result
//}
//
//fun add(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
//    val result = Array(a.size) { FloatArray(a[0].size) }
//
//    for (i in a.indices) {
//        for (j in a[0].indices) {
//            result[i][j] = a[i][j] + b[i][j]
//        }
//    }
//    return result
//}
//
//
//fun relu(x: FloatArray): FloatArray {
//    return x.map { if (it > 0) it else 0f }.toFloatArray()
//}
//
//fun softmax(x: FloatArray): FloatArray {
//    val max = x.maxOrNull() ?: 0f
//    val exps = x.map { exp((it - max).toDouble()).toFloat() }
//    val sum = exps.sum()
//
//    return exps.map { it / sum }.toFloatArray()
//}
//
//
//class NeuralNetwork {
//    private val inputSize = 784
//    private val hiddenSize = 128
//    private val outputSize = 10
//
//    private var W1 = Array(hiddenSize) { FloatArray(inputSize) { randomWeight() } }
//    private var b1 = FloatArray(hiddenSize) { 0f }
//    private var W2 = Array(outputSize) { FloatArray(hiddenSize) { randomWeight() } }
//    private var b2 = FloatArray(outputSize) { 0f }
//
//    private fun randomWeight() = ((Math.random().toFloat() - 0.5f) * 2f / sqrt(inputSize.toDouble()).toFloat())
//
//    fun predict(input: FloatArray): Int {
//        val (_, output) = forward(input)
//        return output.indices.maxByOrNull { output[it] } ?: -1
//    }
//
//    fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
//        val hidden = FloatArray(hiddenSize)
//        for (i in 0 until hiddenSize) {
//            var sum = 0f
//            for (j in 0 until inputSize) sum += W1[i][j] * input[j]
//            hidden[i] = sum + b1[i]
//        }
//        val activated = relu(hidden)
//
//        val output = FloatArray(outputSize)
//        for (i in 0 until outputSize) {
//            var sum = 0f
//            for (j in 0 until hiddenSize) sum += W2[i][j] * activated[j]
//            output[i] = sum + b2[i]
//        }
//        return Pair(activated, softmax(output))
//    }
//
//    fun trainStep(input: FloatArray, label: Int, lr: Float) {
//        val (hiddenActivated, output) = forward(input)
//
//        val dOutput = output.copyOf()
//        dOutput[label] -= 1f
//
//        for (i in 0 until outputSize) {
//            for (j in 0 until hiddenSize) {
//                W2[i][j] -= lr * dOutput[i] * hiddenActivated[j]
//            }
//            b2[i] -= lr * dOutput[i]
//        }
//
//        val dHidden = FloatArray(hiddenSize)
//        for (j in 0 until hiddenSize) {
//            var error = 0f
//            for (i in 0 until outputSize) {
//                error += W2[i][j] * dOutput[i]
//            }
//            dHidden[j] = if (hiddenActivated[j] > 0) error else 0f
//        }
//
//        for (i in 0 until hiddenSize) {
//            for (j in 0 until inputSize) {
//                W1[i][j] -= lr * dHidden[i] * input[j]
//            }
//            b1[i] -= lr * dHidden[i]
//        }
//    }
//
//    private fun relu(x: FloatArray) = x.map { if (it > 0) it else 0f }.toFloatArray()
//
//    private fun softmax(x: FloatArray): FloatArray {
//        val max = x.maxOrNull() ?: 0f
//        val exps = x.map { exp((it - max).toDouble()).toFloat() }
//        val sum = exps.sum()
//        return exps.map { it / sum }.toFloatArray()
//    }
//
//    // СОХРАНЕНИЕ И ЗАГРУЗКА
//    fun saveModel(file: File) {
//        DataOutputStream(FileOutputStream(file)).use { dos ->
//            dos.writeInt(inputSize); dos.writeInt(hiddenSize); dos.writeInt(outputSize)
//            W1.forEach { row -> row.forEach { dos.writeFloat(it) } }
//            b1.forEach { dos.writeFloat(it) }
//            W2.forEach { row -> row.forEach { dos.writeFloat(it) } }
//            b2.forEach { dos.writeFloat(it) }
//        }
//    }
//
//    fun loadModel(file: File) {
//        if (!file.exists()) return
//        DataInputStream(FileInputStream(file)).use { dis ->
//            if (dis.readInt() != inputSize || dis.readInt() != hiddenSize || dis.readInt() != outputSize) return
//            for (i in 0 until hiddenSize) for (j in 0 until inputSize) W1[i][j] = dis.readFloat()
//            for (i in 0 until hiddenSize) b1[i] = dis.readFloat()
//            for (i in 0 until outputSize) for (j in 0 until hiddenSize) W2[i][j] = dis.readFloat()
//            for (i in 0 until outputSize) b2[i] = dis.readFloat()
//        }
//    }
//}
//
//fun crossEntropy(pred: FloatArray, target: Int): Float {
//    return -ln(pred[target])
//}
//
//
//fun main() {
//    val nn = NeuralNetwork()
//
//    val modelFile = File("model.dat")
//
//    val dataset = loadMnistFromFiles(
//        "C:\\Users\\HOME\\StudioProjects\\UniversityMapProj\\app\\src\\com.example.universitymapproj.main\\assets\\mnist\\train-images.idx3-ubyte",
//        "C:\\Users\\HOME\\StudioProjects\\UniversityMapProj\\app\\src\\com.example.universitymapproj.main\\assets\\mnist\\train-labels.idx1-ubyte"
//    )
//    dataset.shuffled()
//
//    if (modelFile.exists()) {
//        nn.loadModel(modelFile)
//        println("Модель загружена!")
//    } else {
//        println("Обучаем новую модель...")
//    }
//
//    for (epoch in 1..10) {
//        for (sample in dataset) {
//            nn.trainStep(sample.input, sample.label, 0.01f)
//        }
//        println("Эпоха $epoch завершена")
//    }
//
//    // СОХРАНЕНИЕ
//    nn.saveModel(modelFile)
//    println("Модель сохранена!")
//}
//
//
//
//
//class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
//    private val paint = Paint().apply {
//        color = Color.BLACK
//        strokeWidth = 3f
//        style = Paint.Style.STROKE
//        strokeCap = Paint.Cap.ROUND
//        isAntiAlias = true
//    }
//
//    private var bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
//    private var canvasBitmap = Canvas(bitmap).apply { drawColor(Color.WHITE) }
//    private var lastX = 0f
//    private var lastY = 0f
//
//    fun clear() {
//        canvasBitmap.drawColor(Color.WHITE)
//        invalidate()
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
//    }
//
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        val x = event.x * 50f / width
//        val y = event.y * 50f / height
//
//        when (event.action) {
//            MotionEvent.ACTION_DOWN -> { lastX = x; lastY = y }
//            MotionEvent.ACTION_MOVE -> {
//                canvasBitmap.drawLine(lastX, lastY, x, y, paint)
//                lastX = x
//                lastY = y
//                invalidate()
//            }
//        }
//        return true
//    }
//
//    fun getNormalizedPixels(): FloatArray {
//        val scaled = Bitmap.createScaledBitmap(bitmap, 28, 28, true)
//        val result = FloatArray(784)
//        for (y in 0 until 28) {
//            for (x in 0 until 28) {
//                val pixel = scaled.getPixel(x, y)
//                // Берем только один канал (например, красный), так как изображение ЧБ
//                val gray = Color.red(pixel)
//                // Инвертируем: в MNIST 0 - фон, 1 - линия. У нас 255 - фон (белый)
//                result[y * 28 + x] = 1f - (gray / 255f)
//            }
//        }
//        return result
//    }
//    private var internalBitmap: Bitmap? = null
//    private var internalCanvas: android.graphics.Canvas? = null
//    fun getBitmap(): Bitmap? {
//        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        val c = android.graphics.Canvas(b)
//        this.draw(c)
//        return b
//    }
//}



package com.example.universitymapproj.NeuralNetwork

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.*
import java.util.zip.GZIPInputStream
import kotlin.math.exp
import kotlin.math.sqrt
import com.example.universitymapproj.models.*
import com.example.universitymapproj.routing.*
import com.example.universitymapproj.clustering.runKMeans
import com.example.universitymapproj.serialization.*
import com.example.universitymapproj.ui.*

class NeuralNetwork {
    private val inputSize = 784
    private val hiddenSize = 128
    private val outputSize = 10

    private var W1 = Array(hiddenSize) { FloatArray(inputSize) { randomWeight() } }
    private var b1 = FloatArray(hiddenSize) { 0f }
    private var W2 = Array(outputSize) { FloatArray(hiddenSize) { randomWeight() } }
    private var b2 = FloatArray(outputSize) { 0f }

    private fun randomWeight() =
        ((Math.random().toFloat() - 0.5f) * 2f / sqrt(inputSize.toDouble()).toFloat())

    fun predict(input: FloatArray): Int {
        val (_, output) = forward(input)
        return output.indices.maxByOrNull { output[it] } ?: -1
    }

    fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(hiddenSize)
        for (i in 0 until hiddenSize) {
            var sum = 0f
            for (j in 0 until inputSize) sum += W1[i][j] * input[j]
            hidden[i] = sum + b1[i]
        }
        val activated = relu(hidden)

        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            var sum = 0f
            for (j in 0 until hiddenSize) sum += W2[i][j] * activated[j]
            output[i] = sum + b2[i]
        }
        return Pair(activated, softmax(output))
    }

    fun trainStep(input: FloatArray, label: Int, lr: Float) {
        val (hiddenActivated, output) = forward(input)

        val dOutput = output.copyOf()
        dOutput[label] -= 1f

        for (i in 0 until outputSize) {
            for (j in 0 until hiddenSize) {
                W2[i][j] -= lr * dOutput[i] * hiddenActivated[j]
            }
            b2[i] -= lr * dOutput[i]
        }

        val dHidden = FloatArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            var error = 0f
            for (i in 0 until outputSize) {
                error += W2[i][j] * dOutput[i]
            }
            dHidden[j] = if (hiddenActivated[j] > 0) error else 0f
        }

        for (i in 0 until hiddenSize) {
            for (j in 0 until inputSize) {
                W1[i][j] -= lr * dHidden[i] * input[j]
            }
            b1[i] -= lr * dHidden[i]
        }
    }

    private fun relu(x: FloatArray) = x.map { if (it > 0) it else 0f }.toFloatArray()

    private fun softmax(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        val exps = x.map { exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    fun saveModel(file: File) {
        DataOutputStream(FileOutputStream(file)).use { dos ->
            dos.writeInt(inputSize)
            dos.writeInt(hiddenSize)
            dos.writeInt(outputSize)

            W1.forEach { row -> row.forEach { dos.writeFloat(it) } }
            b1.forEach { dos.writeFloat(it) }
            W2.forEach { row -> row.forEach { dos.writeFloat(it) } }
            b2.forEach { dos.writeFloat(it) }
        }
    }

    fun loadModel(file: File) {
        if (!file.exists()) return

        DataInputStream(FileInputStream(file)).use { dis ->
            if (dis.readInt() != inputSize ||
                dis.readInt() != hiddenSize ||
                dis.readInt() != outputSize) return

            for (i in 0 until hiddenSize) {
                for (j in 0 until inputSize) {
                    W1[i][j] = dis.readFloat()
                }
            }
            for (i in 0 until hiddenSize) b1[i] = dis.readFloat()

            for (i in 0 until outputSize) {
                for (j in 0 until hiddenSize) {
                    W2[i][j] = dis.readFloat()
                }
            }
            for (i in 0 until outputSize) b2[i] = dis.readFloat()
        }
    }
}

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private lateinit var bitmap: Bitmap
    private lateinit var canvasBitmap: Canvas

    init {
        post {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvasBitmap = Canvas(bitmap)
            canvasBitmap.drawColor(Color.WHITE)
        }
    }

    fun clear() {
        if (::canvasBitmap.isInitialized) {
            canvasBitmap.drawColor(Color.WHITE)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (::bitmap.isInitialized) {
            canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!::canvasBitmap.isInitialized) return true

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                canvasBitmap.drawPoint(event.x, event.y, paint)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val historySize = event.historySize
                for (i in 0 until historySize) {
                    val historicalX = event.getHistoricalX(i)
                    val historicalY = event.getHistoricalY(i)
                    if (i == 0) {
                        canvasBitmap.drawLine(
                            historicalX, historicalY,
                            event.x, event.y, paint
                        )
                    } else {
                        canvasBitmap.drawLine(
                            event.getHistoricalX(i - 1), event.getHistoricalY(i - 1),
                            historicalX, historicalY, paint
                        )
                    }
                }
                invalidate()
            }
        }
        return true
    }

    fun getNormalizedPixels(): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 28, 28, true)
        val result = FloatArray(784)

        for (y in 0 until 28) {
            for (x in 0 until 28) {
                val pixel = scaled.getPixel(x, y)
                val gray = Color.red(pixel)
                result[y * 28 + x] = 1f - (gray / 255f)
            }
        }
        return result
    }

    fun getBitmap(): Bitmap? {
        return if (::bitmap.isInitialized) bitmap else null
    }
}

class NeuralNetworkTrainer(
    private val context: Context,
    private val onProgress: (Int, Int) -> Unit = { _, _ -> },
    private val onComplete: () -> Unit = {}
) {
    private val nn = NeuralNetwork()
    private val modelFile = File(context.filesDir, "model.dat")

    fun train() {
        Thread {
            try {
                if (modelFile.exists()) {
                    nn.loadModel(modelFile)
                    onComplete()
                    return@Thread
                }

                val dataset = loadMnistFromAssets()
                if (dataset.isEmpty()) {
                    trainWithSyntheticData()
                } else {
                    trainWithMnist(dataset)
                }

                nn.saveModel(modelFile)
                onComplete()

            } catch (e: Exception) {
                e.printStackTrace()
                nn.saveModel(modelFile)
                onComplete()
            }
        }.start()
    }

    private fun loadMnistFromAssets(): List<MnistSample> {
        return try {
            val imagesStream = GZIPInputStream(context.assets.open("mnist/train-images.idx3-ubyte"))
            val labelsStream = GZIPInputStream(context.assets.open("mnist/train-labels.idx1-ubyte"))

            readInt(imagesStream)
            val numImages = readInt(imagesStream)
            val rows = readInt(imagesStream)
            val cols = readInt(imagesStream)

            readInt(labelsStream)
            val numLabels = readInt(labelsStream)

            val dataset = mutableListOf<MnistSample>()
            val samplesToLoad = minOf(10000, numImages)

            for (i in 0 until samplesToLoad) {
                val image = FloatArray(rows * cols)
                for (j in 0 until rows * cols) {
                    image[j] = imagesStream.read() / 255f
                }
                val label = labelsStream.read()
                dataset.add(MnistSample(image, label))

                if (i % 1000 == 0) {
                    onProgress(i, samplesToLoad)
                }
            }

            imagesStream.close()
            labelsStream.close()

            dataset.shuffled()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun trainWithMnist(dataset: List<MnistSample>) {
        val epochs = 5
        val learningRate = 0.01f

        for (epoch in 1..epochs) {
            dataset.forEachIndexed { index, sample ->
                nn.trainStep(sample.input, sample.label, learningRate)
                if (index % 500 == 0) {
                    onProgress(epoch * dataset.size + index, epochs * dataset.size)
                }
            }
        }
    }

    private fun trainWithSyntheticData() {
        val epochs = 10
        val learningRate = 0.01f
        val samplesPerDigit = 100

        for (epoch in 1..epochs) {
            for (digit in 0..9) {
                repeat(samplesPerDigit) {
                    val syntheticInput = generateSyntheticDigit(digit)
                    nn.trainStep(syntheticInput, digit, learningRate)
                }
            }
            onProgress(epoch, epochs)
        }
    }

    private fun generateSyntheticDigit(digit: Int): FloatArray {
        val input = FloatArray(784) { 0f }
        val center = 14
        val size = 12

        when (digit) {
            0 -> {
                for (i in 0 until 28) {
                    for (j in 0 until 28) {
                        val dx = i - center
                        val dy = j - center
                        val dist = sqrt((dx * dx + dy * dy).toDouble())
                        if (dist in 6.0..10.0) {
                            input[i * 28 + j] = 1f
                        }
                    }
                }
            }
            1 -> {
                for (i in 0 until 28) {
                    if (i in 4..24) {
                        input[i * 28 + center] = 1f
                    }
                }
            }
            else -> {
                for (i in 0 until 28) {
                    for (j in 0 until 28) {
                        if (i % 4 == 0 && j % 4 == 0) {
                            input[i * 28 + j] = 1f
                        }
                    }
                }
            }
        }

        for (i in input.indices) {
            if (Math.random() < 0.05) {
                input[i] = if (input[i] > 0.5f) 0f else 1f
            }
        }

        return input
    }

    fun getNetwork(): NeuralNetwork = nn

    private fun readInt(input: InputStream): Int {
        val b = IntArray(4) { input.read() }
        return (b[0] shl 24) or (b[1] shl 16) or (b[2] shl 8) or b[3]
    }
}

data class MnistSample(
    val input: FloatArray,
    val label: Int
)