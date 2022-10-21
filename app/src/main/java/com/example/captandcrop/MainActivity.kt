package com.example.captandcrop


import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import com.example.captandcrop.databinding.ActivityMainBinding
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.yalantis.ucrop.UCrop
import java.io.*
import java.lang.ref.WeakReference
import java.util.*


class MainActivity : AppCompatActivity() {
    //    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//    }
    lateinit var binding: ActivityMainBinding

    private val GALLERY_REQUEST_CODE = 1234
    private val WRITE_EXTERNAL_STORAGE_CODE=1

    lateinit var activityResultLauncher:ActivityResultLauncher<Intent>

    lateinit var finalUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding=DataBindingUtil.setContentView(this,R.layout.activity_main)

        setContentView(binding.root)





        checkPermission()
        requestPermission()



        binding.gallery.setOnClickListener {

            if (checkPermission()) {

                pickFromGallery()
            }

            else{
                Toast.makeText(this, "Allow all permissions", Toast.LENGTH_SHORT).show()
                requestPermission()
            }

        }

        binding.camera.setOnClickListener {

            if (checkPermission()) {

                pickFromCamera()
            }

            else{
                Toast.makeText(this, "Allow all permissions", Toast.LENGTH_SHORT).show()
                requestPermission()
            }

        }

        binding.save.setOnClickListener {


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED){

                    val permission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

                    requestPermissions(permission,WRITE_EXTERNAL_STORAGE_CODE)


                }
                else{

                    saveEditedImage()

                }


            }


        }

        binding.cancel.setOnClickListener {

            val builder = AlertDialog.Builder(this)
            builder.setMessage("DO YOU WANT TO CANCEL?")
            builder.setPositiveButton("YES") { dialog, which ->

                binding.image.visibility=View.GONE
                binding.cancel.visibility=View.GONE
                binding.save.visibility=View.GONE
                binding.selectToast.visibility=View.VISIBLE
                binding.camera.visibility=View.VISIBLE
                binding.gallery.visibility=View.VISIBLE




            }
            builder.setNegativeButton("NO")
            { dialog, which -> }

            val alertDialog = builder.create()


            alertDialog.window?.setGravity(Gravity.BOTTOM)

            alertDialog.show()


        }


        activityResultLauncher  =
            registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->


                if (result.resultCode== RESULT_OK) {

                    var extras: Bundle? = result.data?.extras

                    var imageUri: Uri

                    var imageBitmap = extras?.get("data") as Bitmap

                    var imageResult: WeakReference<Bitmap> = WeakReference(
                        Bitmap.createScaledBitmap(
                            imageBitmap, imageBitmap.width, imageBitmap.height, false
                        ).copy(
                            Bitmap.Config.RGB_565, true
                        )
                    )

                    var bm = imageResult.get()

                    imageUri = saveImage(bm, this)



                    launchImageCrop(imageUri)



                }

                else{



                }



            }


    }





    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode) {

            WRITE_EXTERNAL_STORAGE_CODE -> {

                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {


                } else {
                    Toast.makeText(this, "Enable permissions", Toast.LENGTH_SHORT).show()
                }

            }



        }

    }


    private fun saveEditedImage() {


        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, finalUri)

        saveMediaToStorage(bitmap)


    }


    private fun saveImage(image: Bitmap?, context: Context): Uri {



        var imageFolder=File(context.cacheDir,"images")
        var uri: Uri? = null

        try {

            imageFolder.mkdirs()
            var file:File= File(imageFolder,"captured_image.png")
            var stream:FileOutputStream= FileOutputStream(file)
            image?.compress(Bitmap.CompressFormat.JPEG,100,stream)
            stream.flush()
            stream.close()
            uri=FileProvider.getUriForFile(context.applicationContext,"com.sunayanpradhan.imagecropper"+".provider",file)


        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }catch (e:IOException){
            e.printStackTrace()
        }

        return uri!!

    }

    private fun pickFromGallery() {

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivityForResult(intent, GALLERY_REQUEST_CODE)
    }


    private fun pickFromCamera(){



        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        activityResultLauncher.launch(intent)



    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)



        when (requestCode) {

            GALLERY_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.let { uri ->
                        launchImageCrop(uri)
                    }
                }

                else{

                }
            }



        }

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri :Uri ?= UCrop.getOutput(data!!)

            setImage(resultUri!!)

            finalUri=resultUri

            binding.image.visibility= View.VISIBLE
            binding.selectToast.visibility=View.GONE
            binding.save.visibility=View.VISIBLE
            binding.cancel.visibility=View.VISIBLE
            binding.camera.visibility=View.GONE
            binding.gallery.visibility=View.GONE

        }

    }

    private fun launchImageCrop(uri: Uri) {


        var destination:String=StringBuilder(UUID.randomUUID().toString()).toString()
        var options:UCrop.Options=UCrop.Options()


        UCrop.of(Uri.parse(uri.toString()), Uri.fromFile(File(cacheDir,destination)))
            .withOptions(options)
            .withAspectRatio(0F, 0F)
            .useSourceImageAspectRatio()
            .withMaxResultSize(2000, 2000)
            .start(this)


    }

    private fun setImage(uri: Uri){
        Glide.with(this)
            .load(uri)
            .into(binding.image)
    }




    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ),
            100
        )
    }

    private fun saveMediaToStorage(bitmap: Bitmap) {
        val filename = "${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(this, "Saved to Photos", Toast.LENGTH_SHORT).show()
        }
    }


}