package com.example.drawingapp


import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.drawingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mImageButtonCurrentPaint: ImageButton
    var customProgressDialog : Dialog? = null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode == RESULT_OK&& result.data!=null){
                binding.ivBackground.setImageURI(result.data?.data)
            }
        }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if(isGranted){
                    Toast.makeText(
                        this,"Permission granted.",Toast.LENGTH_LONG

                    ).show()
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                }else{
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(
                            this,"Permission denied.",Toast.LENGTH_LONG

                        ).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.drawingView.setSizeforBrush(20.toFloat())
        mImageButtonCurrentPaint = binding.ibCurrentColor
        mImageButtonCurrentPaint.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )
        binding.ibBrush.setOnClickListener{
            showBrushSizeDialog()
        }
        binding.ibColor.setOnClickListener{
            showColorPickerDialog()
        }
        binding.ibImage.setOnClickListener{
            requestStoragePermission()
        }
        binding.ibUndo.setOnClickListener{
            binding.drawingView.onClickUndo()
        }
        binding.ibSave.setOnClickListener{
            if(isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    saveBitmapFile(getBitMapFromView(binding.flDrawingViewContainer))
                }
            }
        }
    }



    private fun showColorPickerDialog(){
        val colorDialog = Dialog(this)
        colorDialog.setContentView(R.layout.dialog_color_picker)
        colorDialog.setTitle("Color: ")
        colorDialog.show()
    }

    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size: ")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            binding.drawingView.setSizeforBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            binding.drawingView.setSizeforBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            binding.drawingView.setSizeforBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun painClicked(view : View){
        if(view != mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            binding.drawingView.setColor(colorTag)
            binding.ibCurrentColor.setBackgroundColor(Color.parseColor(colorTag))

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal))

            mImageButtonCurrentPaint = view
        }

    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermission(){
            if(ActivityCompat.shouldShowRequestPermissionRationale
                    (this,Manifest.permission.READ_EXTERNAL_STORAGE)){
                showRationalDialog("Drawing App","Drawing App " +"need to Access Your External Storage")
            }else{
                requestPermission.launch(arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }
    }
    private fun getBitMapFromView(view :View) : Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bg = view.findViewById<ImageView>(R.id.iv_background)
        if(bg != null){
            bg.draw(canvas)
        } else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun showRationalDialog(title : String , message : String){
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel"){
            dialog,_ ->dialog.dismiss()
        }
        builder.create().show()
    }
    private suspend fun saveBitmapFile(mBitmap: Bitmap?):String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90, bytes)
                    //Location + name
                    val f = File(externalCacheDir?.absoluteFile.toString()
                            + File.separator + "Drawing App" +System.currentTimeMillis
                    ()/1000 + ".png")
                    val fout = FileOutputStream(f)
                    fout.write(bytes.toByteArray())
                    fout.close()

                    result =  f.absolutePath

                    runOnUiThread{
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,"File saved sucessfully : $result",Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(
                                this@MainActivity,"Something went wrong",Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }

            }

        }
        return result
    }
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }
    private fun shareImage(result : String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }
}