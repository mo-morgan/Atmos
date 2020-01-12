package com.example.atmos;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class RecordActivity extends AppCompatActivity {
    // For debugging
    private final String LOG_TAG  = "MorganDebug";

    private final String Url = "https://nwservers.azurewebsites.net:443/faces";

    // Video
    private MediaRecorder mMediaRecorder = null;

    private boolean started = false;
    private Camera mCamera;
    private CameraPreview cameraPreview;


    private boolean isRecording = false;

    private Handler mPeriodicVolumeCheckHandler;

    private static File mediaStorageDir;

    private static String currentFileName = null;
    private static String currentFilePath = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_record);

        if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissions();
        }
        // if doesn't have camera
        if (checkCameraHardware(this)) {
            mCamera = getCameraInstance();
        }
        cameraPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraPreview);

        // Add a listener to the Capture button
        final Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isRecording) {
                            // stop recording and release camera
                            mMediaRecorder.stop();  // stop the recording
                            releaseMediaRecorder(); // release the MediaRecorder object
                            mCamera.lock();         // take camera access back from MediaRecorder

                            // inform the user that recording has stopped
                            captureButton.setText("Capture");
                            isRecording = false;

                            if (mediaStorageDir != null && currentFilePath != null && currentFileName != null) {
                                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                                mediaMetadataRetriever.setDataSource(currentFilePath);
                                mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.OPTION_CLOSEST);
//                                File fileToSend = new File(currentFilePath);
                                Bitmap bm = mediaMetadataRetriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST);
//                                Log.d(LOG_TAG, String.valueOf(bm.getHeight()));

                                String attachmentName = currentFileName;
                                String attachmentFileName = currentFileName + ".bmp";
                                String crlf = "\r\n";
                                String twoHyphens = "--";
                                String boundary =  "*****";

                                try {
                                    HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(Url).openConnection();
                                    httpURLConnection.setUseCaches(false);
                                    httpURLConnection.setDoOutput(true);

                                    httpURLConnection.setRequestMethod("POST");
                                    httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
                                    httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
                                    httpURLConnection.setRequestProperty(
                                            "Content-Type", "multipart/form-data;boundary=" + boundary);

                                    DataOutputStream request = new DataOutputStream(
                                            httpURLConnection.getOutputStream());

                                    request.writeBytes(twoHyphens + boundary + crlf);
                                    request.writeBytes("Content-Disposition: form-data; name=\"" +
                                            attachmentName + "\";filename=\"" +
                                            attachmentFileName + "\"" + crlf);
                                    request.writeBytes(crlf);

                                    byte[] pixels = new byte[bm.getWidth() * bm.getHeight()];

                                    request.write(pixels);
                                    request.writeBytes(crlf);
                                    request.writeBytes(twoHyphens + boundary +
                                            twoHyphens + crlf);

                                    request.flush();
                                    request.close();

                                    InputStream responseStream = new
                                            BufferedInputStream(httpURLConnection.getInputStream());

                                    BufferedReader responseStreamReader =
                                            new BufferedReader(new InputStreamReader(responseStream));

                                    String line = "";
                                    StringBuilder stringBuilder = new StringBuilder();

                                    while ((line = responseStreamReader.readLine()) != null) {
                                        stringBuilder.append(line).append("\n");
                                    }
                                    responseStreamReader.close();

                                    // JSON response
                                    String response = stringBuilder.toString();

                                    responseStream.close();

                                    httpURLConnection.disconnect();


                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            // initialize video camera
                            if (prepareVideoRecorder()) {
                                // Camera is available and unlocked, MediaRecorder is prepared,
                                // now you can start recording
                                mMediaRecorder.start();

                                // inform the user that recording has started
                                captureButton.setText("Stop");
                                isRecording = true;
                            } else {
                                // prepare didn't work, release the camera
                                releaseMediaRecorder();
                                // inform user
                            }
                        }
                    }
                }
        );
    }

    private boolean prepareVideoRecorder(){
        mCamera = getCameraInstance();
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO).toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(LOG_TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private static Uri getOutputMediaFileUri(int type){
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
            currentFilePath = mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4";
            currentFileName = "VID_" + timeStamp;
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    /** Check if this device has a camera */
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(RecordActivity.this,
                new String[]{Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.INTERNET,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA},
                1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(RecordActivity.this, "Permission denied to record audio", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public double getAmplitude() {
        if (mMediaRecorder != null)
            return  mMediaRecorder.getMaxAmplitude();
        else
            return 0;

    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera camera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            this.camera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                Log.d(LOG_TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.

        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                camera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                camera.setPreviewDisplay(mHolder);
                camera.startPreview();

            } catch (Exception e){
                Log.d(LOG_TAG, "Error starting camera preview: " + e.getMessage());
            }
        }
    }
//
//    private class VideoRecordThread implements Runnable {
//        Thread thread;
//
//        public VideoRecordThread() {
//            thread = new Thread();
//            thread.start();
//        }
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                Camera.open();
//                Camera.
//            }
//        }
//    }

//    private class IncomingHandlerCallback implements Handler.Callback {
//        @Override
//        public boolean handleMessage(Message msg) {
//            Bundle bundle = msg.getData();
//            double amp = bundle.getDouble("key");
//
//            Log.d(LOG_TAG, String.valueOf(amp));
//            TextView textView = (TextView) findViewById(R.id.number);
//            textView.setText(String.valueOf(amp));
//
//            return true;
//        }
//    }

    private class AsyncUploadBitmaps extends AsyncTask<Bitmap, Void, String> {

        @Override
        protected String doInBackground(Bitmap... bitmaps) {
            return null;
        }
    }

    private class DetectVolume implements Runnable {
        private Thread thread;

        public DetectVolume() {
            Log.d(LOG_TAG, "starting thread");
            thread = new Thread(this);
            thread.start();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    this.thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Message msg = mPeriodicVolumeCheckHandler.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putDouble("key", getAmplitude());
                msg.setData(bundle);
                mPeriodicVolumeCheckHandler.sendMessage(msg);
                if (!started) {
                    break;
                }
            }
        }
    }
}
