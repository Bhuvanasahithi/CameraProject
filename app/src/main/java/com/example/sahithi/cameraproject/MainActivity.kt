package com.example.sahithi.cameraproject

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.support.annotation.RequiresApi
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.widget.Button
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.Buffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    var ORIENTATIONS = SparseIntArray()
    var textureView: TextureView? = null
    var captureImage: Button? = null
    var previewSize: Size? = null
    var cameraDevice: CameraDevice? = null
    var previewBuilder: CaptureRequest.Builder? = null
    var previewSession: CameraCaptureSession? = null
    var jPEGSIzes: Array<Size>? = null
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //requesting the permissions
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), 1
            )
        }
        else {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
            //initializing the views
            textureView = findViewById(R.id.texture_view) as TextureView
            captureImage = findViewById(R.id.btn_save_picture) as Button
            //setting the surface texture listener for texture view
            textureView!!.setSurfaceTextureListener(surfaceTextureListener)
            //button capture image action
            captureImage!!.setOnClickListener()
            { v: View ->
                getImage()
                Toast.makeText(this@MainActivity,"Image saved successfully",Toast.LENGTH_LONG).show()
            }

        }

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getImage() {
        //camera device -null or not
        if (cameraDevice == null) {
            return
        }
        var manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraCharacteristics = manager.getCameraCharacteristics(cameraDevice!!.getId())
        if (cameraCharacteristics != null)
            jPEGSIzes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.JPEG)
        //specifying the default width and height
        var width = 640
        var height = 480
        if (jPEGSIzes != null && jPEGSIzes!!.size > 0) {
            width = jPEGSIzes!!.get(0).width
            height = jPEGSIzes!!.get(0).height

        }
        //image reader
        var reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1)
        //creating list of surfaces for capturing images
        var outputSurfaces = ArrayList<Surface>(2)
        outputSurfaces.add(reader.getSurface())
        outputSurfaces.add(Surface(textureView!!.surfaceTexture))
        var capturebuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        capturebuilder.addTarget(reader.getSurface())
        capturebuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        var rotation = getWindowManager().defaultDisplay.rotation
        capturebuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation))
        //image available listener
        var imageAvailableListener = object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader) {
                var image: Image? = null
                image = reader.acquireLatestImage()
                var byteBuffer = image.getPlanes().get(0).buffer
                var bytes = ByteArray(byteBuffer.capacity())
                byteBuffer.get(bytes)
                save(bytes)
            }

        }

        var handlerThread=HandlerThread("take picture")
        handlerThread.start()
        var handler=Handler(handlerThread.looper)
        reader.setOnImageAvailableListener(imageAvailableListener,handler)
        //capture callback
        var previewSSession=object:CameraCaptureSession.CaptureCallback()
        {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
                startCamera()
            }
        }

        cameraDevice!!.createCaptureSession(outputSurfaces,object:CameraCaptureSession.StateCallback()
        {
            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

            override fun onConfigured(session: CameraCaptureSession) {
                session.capture(capturebuilder.build(),previewSSession,handler)
            }

        },handler)
    }

    private fun save(bytes: ByteArray) {
        var file = getOutputMediaFile()
        var outputStream:OutputStream
        outputStream=FileOutputStream(file)
        outputStream.write(bytes)
    }

    private fun getOutputMediaFile(): File? {
        var mediaStorageFile = File(Environment.getExternalStorageDirectory(), "MyCameraApp")
        if(!mediaStorageFile.exists())
        {
            if(!mediaStorageFile.mkdirs())
            {
                Log.d("directory creation","failed to create the specified directory")
                return null
            }

        }
        var timestamp:String=SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
       var mediaFile =  File(mediaStorageFile.getPath() + File.separator
                + "IMG_" + timestamp + ".jpg");
        return mediaFile
    }



    //callback of surface texture listener
    var surfaceTextureListener=object:SurfaceTextureListener
    {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {

        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
            return false
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            openCamera()
        }

    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun openCamera() {
        //creating the camera manager to access the available devices
        var manager=getSystemService(Context.CAMERA_SERVICE) as CameraManager
        //getting the camera id
        var cameraId=manager.cameraIdList.get(0)
        //getting the characteristics for the camera id
        var characteristics=manager.getCameraCharacteristics(cameraId)
        Log.d("cameraid",cameraId+" "+manager.cameraIdList.size)
        //getting the preview size
        var map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
       previewSize=map.getOutputSizes(SurfaceTexture::class.java).get(0)
        Log.d("camera preview size",map.getOutputSizes(SurfaceTexture::class.java).toString())
        //opening the camera by checking whether the camera device is previously opened or not
        manager.openCamera(cameraId,stateCallback,null)

    }
    var stateCallback= @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object:CameraDevice.StateCallback()
    {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice=camera
            startCamera()
        }

        override fun onDisconnected(camera: CameraDevice) {

        }

        override fun onError(camera: CameraDevice, error: Int) {

        }

    }

    @TargetApi(Build.VERSION_CODES.P)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCamera() {
        //checking whether the cameradevice,textureview surface is null or not,
        if(cameraDevice==null||!textureView!!.isAvailable||previewSize==null)
        {
            return
        }
        var surfaceTexture= textureView!!.getSurfaceTexture() as SurfaceTexture
        if(surfaceTexture==null)
        {
            return
        }
        //setting the height and width for surface texture
        surfaceTexture.setDefaultBufferSize(previewSize!!.getWidth(),previewSize!!.getHeight())
        var surface=Surface(surfaceTexture)
        //creating the capture request
        previewBuilder=cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewBuilder!!.addTarget(surface)
        //camera capture session
        cameraDevice!!.createCaptureSession(Arrays.asList(surface),object :CameraCaptureSession.StateCallback()
        {
            override fun onConfigureFailed(session: CameraCaptureSession) {

            }

            override fun onConfigured(session: CameraCaptureSession) {
                previewSession=session
                getChangedPreview()
            }

        },null)
        }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getChangedPreview() {
        if(cameraDevice==null)
        {
            return
        }
        previewBuilder!!.set(CaptureRequest.CONTROL_MODE,CameraMetadata.CONTROL_MODE_AUTO)
        //handler thread
        var handlerThread=HandlerThread("capture Request")
        handlerThread.start()
        //handler
        var handler=Handler(handlerThread.looper)
        previewSession!!.setRepeatingRequest(previewBuilder!!.build(),null,handler)


    }


}



