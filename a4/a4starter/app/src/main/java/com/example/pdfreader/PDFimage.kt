package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.annotation.RequiresPermission.Read
import androidx.core.graphics.toRect

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing path
    var path: Path? = null
    var paths = mutableListOf<Path?>()

    var highlightPaths = mutableListOf<Path?>()

    // image to display
    var bitmap: Bitmap? = null
    var paint = Paint(Color.BLUE)

    // For panning
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    // For zooming
    var scaleFactor = 1.0f
    private val scaleGestureDetector: ScaleGestureDetector
    private val mainActivity = context as MainActivity

    var pathStack = ArrayDeque<Pair<Path, String>>()
    var redoPathStack = ArrayDeque<Pair<Path, String>>()



    private val strokeMap = mutableMapOf<Path, Paint>()
//
//    private val gestureDetector: GestureDetector

    private var adjustedX: Float = -500f // start off screen
    private var adjustedY: Float = -500f

    var pathPageMap = mutableMapOf<Path, Int>()
    var highlightPathPageMap = mutableMapOf<Path, Int>()





    init {
        paint.style = Paint.Style.STROKE
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                detector?.let {
                    ReaderModel.allowPan = false
                    scaleFactor *= (it.scaleFactor)

                }
                return true
            }

        })
    }

    val penPaint: Paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    val highlighterPaint: Paint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 10f
        style = Paint.Style.STROKE
        // make it transparent
        alpha = 50

    }

    fun useHighlighter() {

        ReaderModel.isHighlighting = true
    }

    fun usePen() {

        ReaderModel.isDrawing = true
    }

    fun zoomOut() {
        scaleFactor = 1.0f
        scaleX = scaleFactor
        scaleY = scaleFactor
    }

    fun erase() {
        val margin = 50f
        // Define the eraser rectangle
        val eraserRect = RectF(adjustedX - margin, adjustedY - margin, adjustedX + margin, adjustedY + margin)

        val pathIterator = paths.iterator()
        while (pathIterator.hasNext()) {
            val path = pathIterator.next()
            if (path != null && intersects(path, eraserRect)) {
                // The path intersects with the eraser
                pathIterator.remove()
                pathStack.add(Pair(path, "ERASE"))
            }
        }

        val highlightPathIterator = highlightPaths.iterator()
        while (highlightPathIterator.hasNext()) {
            val path = highlightPathIterator.next()
            if (path != null && intersects(path, eraserRect)) {
                highlightPathIterator.remove()
                pathStack.add(Pair(path, "ERASE"))
            }
        }
    }

    fun intersects(path: Path, rect: RectF): Boolean {
        val pm = PathMeasure(path, false)
        val length = pm.length
        val size = 1000
        val step = length / size

        for (i in 0..size) {
            val point = floatArrayOf(0f, 0f)
            pm.getPosTan(i * step, point, null)

            // if point falls within the eraser rectangle, path intersects with the rectangle
            if (rect.contains(point[0], point[1])) {
                return true
            }
        }

        return false
    }




    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount


        if (pointerCount > 1) {
            scaleGestureDetector.onTouchEvent(event)
            scaleX = scaleFactor
            scaleY = scaleFactor

            return true
        }


        adjustedX = (event.x )
        adjustedY = (event.y )
        println("$adjustedX $adjustedY")


        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(LOGNAME, "Action down")

                if (ReaderModel.isDrawing || ReaderModel.isHighlighting) {
                    ReaderModel.lastAction = "DOWN"
                    path = Path()
                    path!!.moveTo(adjustedX, adjustedY)
                    val currentPageIndex = mainActivity.currentPage?.index ?: 0
                    if (ReaderModel.isDrawing) {
                        paths.add(path)
                        pathPageMap[path!!] = currentPageIndex
                        pathStack.add(Pair(path!!, "PEN"))
                        ReaderModel.lastPaint = "PEN"

                    }
                    else if (ReaderModel.isHighlighting) {
                        highlightPaths.add(path)
                        highlightPathPageMap[path!!] = currentPageIndex
                        pathStack.add(Pair(path!!, "HIGHLIGHTER"))
                        ReaderModel.lastPaint = "HIGHLIGHTER"
                    }
                } else {
                    lastTouchX = adjustedX
                    lastTouchY = adjustedY
                }
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d(LOGNAME, "Action move")
                if (ReaderModel.isErasing) {
                    ReaderModel.lastAction = "ERASE"
                    erase()
                }
                if (ReaderModel.isDrawing) {
                    // make a copy of path
                    paths.last()?.lineTo(adjustedX, adjustedY)
                    // display the path


                } else if (ReaderModel.isHighlighting) {
                    highlightPaths.last()?.lineTo(adjustedX, adjustedY)

                } else if (ReaderModel.allowPan) {
                    val dx = adjustedX - lastTouchX
                    val dy = adjustedY - lastTouchY

                    scrollBy(-dx.toInt(), -dy.toInt())

                    lastTouchX = adjustedX
                    lastTouchY = adjustedY
                }
            }

            MotionEvent.ACTION_UP -> {
                Log.d(LOGNAME, "Action up")
                if (!ReaderModel.isDrawing && !ReaderModel.isHighlighting && !ReaderModel.isErasing) {
                    ReaderModel.allowPan = true
                } else {
                    redoPathStack.clear()
                }

            }
        }

        return true
    }

    fun undo() {
        if (pathStack.isNotEmpty()) {
            val pathPair = pathStack.removeLast()
            val path = pathPair.first
            val type = pathPair.second
            if (type.equals("PEN")) {
                paths.remove(path)
            } else if (type.equals("HIGHLIGHTER")) {
                highlightPaths.remove(path)
            } else if (type.equals("ERASE")) {
                paths.add(path)
                highlightPaths.add(path)
            }
            redoPathStack.add(pathPair)

        }
    }

    fun redo() {

        if (redoPathStack.isNotEmpty()) {
            println("redo")
            val pathPair = redoPathStack.removeLast()
            val path = pathPair.first
            val type = pathPair.second
            if (type.equals("PEN")) {
                paths.add(path)

            } else if (type.equals("HIGHLIGHTER")) {
                highlightPaths.add(path)

            } else if (type.equals("ERASE")) {
                paths.remove(path)
                highlightPaths.remove(path)

            }
            pathStack.add(pathPair)
        }
    }





    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
        for (path in paths) {
            path?.let { strokeMap.put(it, paint) }
        }
        for (path in highlightPaths) {
            path?.let { strokeMap.put(it, paint) }
        }
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    fun setBrush(paint: Paint) {
        this.paint = paint
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }

        val currentPageIndex = mainActivity.currentPage?.index ?: 0
        // draw lines over it
        for (path in paths) {
            if (pathPageMap[path] == currentPageIndex) {
                path?.let { canvas.drawPath(it, penPaint) }
            }

        }

        for (path in highlightPaths) {
            if (highlightPathPageMap[path] == currentPageIndex) {
                path?.let { canvas.drawPath(it, highlighterPaint) }
            }
        }

        if (ReaderModel.isErasing) {
            // Define the erasing region.
            val margin = 50f
            rect = RectF(adjustedX - margin, adjustedY - margin, adjustedX + margin, adjustedY + margin)

            // Set the paint style.
            val paint = Paint()
            paint.color = Color.RED
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f

            // Draw the rect on canvas.
            canvas.drawRect(rect, paint)


        }
    }

    private var rect = RectF()

    fun draw() {
        println(paths.size)
        for (path in paths) {
            path?.let { strokeMap.put(it, paint) }
            print("Drawing path")
        }
        for (path in highlightPaths) {
            path?.let { strokeMap.put(it, paint) }
            print("Drawing path")
        }
    }


    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        println("Configuration changed")
        draw()
    }

    fun useEraser() {
        ReaderModel.allowPan = false
        ReaderModel.isDrawing = false
        ReaderModel.isHighlighting = false
        ReaderModel.isErasing = true
        ReaderModel.allowPan = false
    }

}