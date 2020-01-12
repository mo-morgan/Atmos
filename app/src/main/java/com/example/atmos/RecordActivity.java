package com.example.atmos;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Console;
import java.io.IOException;

public class RecordActivity extends AppCompatActivity {
    private final String LOG_TAG  = "MorganDebug";

    private MediaRecorder mRecorder = null;

    private boolean started = false;

    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!checkIfAlreadyHavePermission()) {
            requestPermissions();
        }

        final Button recordButton = (Button) findViewById(R.id.record_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (started) {
                        stop();
                        started = false;
                        recordButton.setText("Start");
                    } else {
                        start();
                        started = true;
                        recordButton.setText("Stop");
                        new DetectVolume();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        mHandler = new Handler(new IncomingHandlerCallback());
    }

    private boolean checkIfAlreadyHavePermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS);

        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(RecordActivity.this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                1);

        ActivityCompat.requestPermissions(RecordActivity.this,
                new String[]{Manifest.permission.INTERNET},
                2);
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

            case 2: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(RecordActivity.this, "Permission denied for internet", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public void start() throws IOException {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");
            mRecorder.prepare();
            mRecorder.start();
        }
    }

    public void stop() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return  mRecorder.getMaxAmplitude();
        else
            return 0;

    }

    private class IncomingHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            double amp = bundle.getDouble("key");

            Log.d(LOG_TAG, String.valueOf(amp));
            TextView textView = (TextView) findViewById(R.id.number);
            textView.setText(String.valueOf(amp));

            return true;
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

                Message msg = mHandler.obtainMessage();
                Bundle bundle = new Bundle();
                bundle.putDouble("key", getAmplitude());
                msg.setData(bundle);
                mHandler.sendMessage(msg);
                if (!started) {
                    break;
                }
            }
        }
    }
}
