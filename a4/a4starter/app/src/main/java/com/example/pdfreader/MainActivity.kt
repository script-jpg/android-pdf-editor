package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    private val viewModel: PathsViewModel by    viewModels()


    lateinit var buttonLayout: LinearLayout

    lateinit var status: TextView

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage






    override fun onCreate(savedInstanceState: Bundle?) {

        var orientation = resources.configuration.orientation;
        println("orientation: $orientation")
        val margin: Int
        margin = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            println("landscape")
            1600
        } else {
            println("portrait")
            2362
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<FrameLayout>(R.id.pdfLayout)
        layout.isEnabled = true



        pageImage = PDFimage(this)

        viewModel.paths?.let {
            pageImage.paths = it

        }

        viewModel.highlightPaths?.let {
            pageImage.highlightPaths = it

        }

        viewModel.pathPageMap?.let {
            pageImage.pathPageMap = it

        }

        viewModel.highlightPathPageMap?.let {
            pageImage.highlightPathPageMap = it

        }
        viewModel.pathStack?.let {
            pageImage.pathStack = it

        }
        viewModel.redoPathStack?.let {
            pageImage.redoPathStack = it

        }








// Creating a new horizontal LinearLayout
        buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

        }

        val eraserToggle = android.widget.Switch(this)

        val highlightToggle = android.widget.Switch(this)
// Add a toggle to toggle drawing otherwise panning
        val drawingToggle = android.widget.Switch(this)
        drawingToggle.text = "Toggle drawing"
        drawingToggle.setOnClickListener {
            if (!ReaderModel.isDrawing) {
                pageImage.usePen()
                highlightToggle.isChecked = false
                eraserToggle.isChecked = false
                ReaderModel.isDrawing = true
                ReaderModel.isHighlighting = false
                ReaderModel.isErasing = false
            }
            else {
                ReaderModel.isDrawing = false
                ReaderModel.allowPan = true
            }


        }


        highlightToggle.text = "Toggle highlighting"
        highlightToggle.setOnClickListener {
            if (!ReaderModel.isHighlighting) {
                pageImage.useHighlighter()
                drawingToggle.isChecked = false
                eraserToggle.isChecked = false
                ReaderModel.isDrawing = false
                ReaderModel.isErasing = false
            } else {
                ReaderModel.isHighlighting = false
                ReaderModel.allowPan = true
            }
        }


        eraserToggle.text = "Toggle eraser"
        eraserToggle.setOnClickListener {
            if (!ReaderModel.isErasing) {
                pageImage.useEraser()
                highlightToggle.isChecked = false
                drawingToggle.isChecked = false
                ReaderModel.isErasing = true
            }
            else {
                ReaderModel.isErasing = false
                ReaderModel.allowPan = true
            }

        }






// Page control - forward page button
        val forwardButton = android.widget.Button(this)
        forwardButton.text = "Next page"
        forwardButton.setOnClickListener {

            openRenderer(this)
            showPage(currentPage!!.index + 1)
            closeRenderer()
        }

        val backButton = android.widget.Button(this)
        backButton.text = "Previous page"
        backButton.setOnClickListener {

            if (currentPage!!.index > 0) {
                openRenderer(this)
                showPage(currentPage!!.index - 1)
                closeRenderer()
            }
            else {
                Log.d(LOGNAME, "Already at first page")
            }
        }

        val undoButton = android.widget.Button(this)
        undoButton.text = "Undo"
        undoButton.setOnClickListener {
            pageImage.undo()
        }

        val redoButton = android.widget.Button(this)
        redoButton.text = "Redo"
        redoButton.setOnClickListener {
            pageImage.redo()
        }








// Adding the buttons to the horizontal LinearLayout
        buttonLayout.addView(undoButton)
        buttonLayout.addView(redoButton)
        buttonLayout.addView(backButton)
        buttonLayout.addView(forwardButton)
        buttonLayout.addView(drawingToggle)
        buttonLayout.addView(highlightToggle)
        buttonLayout.addView(eraserToggle)
        buttonLayout.background = getDrawable(androidx.appcompat.R.color.material_grey_300)
        buttonLayout.setOnClickListener {
            println("button layout clicked")




        }
// Adding the horizontal LinearLayout to the main layout


        layout.addView(pageImage)

        val title = TextView(this).apply {
            text = FILENAME
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = getDrawable(androidx.appcompat.R.color.material_grey_300)


            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )



            params.setMargins(0, 0, 0, 0)


            layoutParams = params


            layout.addView(this)
        }



        var params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        params.setMargins(0, 39, 0, 0)


        layout.addView(buttonLayout, params)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 1300

        // status bar to show page info
        status = TextView(this).apply {
            text = "Page 0"
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = getDrawable(androidx.appcompat.R.color.material_grey_300)


            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )



            params.setMargins(0, margin, 0, 0)


            layoutParams = params


            layout.addView(this)
        }




        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            if (viewModel.page != null) {
                showPage(viewModel.page!!)
            }
            else {
                showPage(0)
            }
            closeRenderer()
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }

    override fun onStop() {
        super.onStop()

        // Save paths to ViewModel
        viewModel.paths = pageImage.paths

        // Save highlights to ViewModel
        viewModel.highlightPaths = pageImage.highlightPaths

        // Save pathPageMap to ViewModel
        viewModel.pathPageMap = pageImage.pathPageMap

        // Save highlightPathPageMap to ViewModel
        viewModel.highlightPathPageMap = pageImage.highlightPathPageMap

        viewModel.pathStack = pageImage.pathStack

        viewModel.redoPathStack = pageImage.redoPathStack



        println("Stopped")
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        pdfRenderer = PdfRenderer(parcelFileDescriptor)



    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        System.out.println("Closing renderer"+currentPage)
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            Log.d(LOGNAME, "Invalid page index")
            return
        }

        viewModel.page = index

                // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)
        status.text = "Page ${index + 1}/"+pdfRenderer.pageCount // Update status text

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(
                currentPage!!.getWidth(),
                currentPage!!.getHeight(),
                Bitmap.Config.ARGB_8888
            )

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)


            // Display the page
            pageImage.setImage(bitmap)
        }

    }









}

class PathsViewModel : ViewModel() {
    var page: Int? = null
    var pdfRenderer: PdfRenderer? = null
    var paths: MutableList<Path?>? = MutableList(0) { null }
    var highlightPaths: MutableList<Path?>? = MutableList(0) { null }
    var pathPageMap = mutableMapOf<Path, Int>()
    var highlightPathPageMap = mutableMapOf<Path, Int>()
    var pathStack = ArrayDeque<Pair<Path, String>>()
    var redoPathStack = ArrayDeque<Pair<Path, String>>()
}
