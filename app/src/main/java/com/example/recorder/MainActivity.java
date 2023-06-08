package com.example.recorder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.jtransforms.fft.DoubleFFT_1D;

public class MainActivity extends AppCompatActivity {

    private TextView textViewStatus;
    private EditText editTextGainFactor;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    private int intBufferSize;
    private short[] shortAudioData;

    private int intGain;
    private boolean isActive = false;

    private Thread thread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PackageManager.PERMISSION_GRANTED);

        textViewStatus = findViewById(R.id.textViewStatus);
        editTextGainFactor = findViewById(R.id.editTextGainFactor);

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                threadLoop();
            }
        });
    }

    public void buttonStart(View view) {
        isActive = true;
        intGain = Integer.parseInt(editTextGainFactor.getText().toString());
        textViewStatus.setText("Active");
        thread.start();
    }

    public void buttonStop(View view) {
        isActive = false;
        audioTrack.stop();
        audioRecord.stop();

        textViewStatus.setText("Stopped");
    }

    private void threadLoop() {
        int intRecordSampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);

        intBufferSize = AudioRecord.getMinBufferSize(intRecordSampleRate, AudioFormat.CHANNEL_IN_MONO
                , AudioFormat.ENCODING_PCM_16BIT);

        shortAudioData = new short[intBufferSize];

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC
                , intRecordSampleRate
                , AudioFormat.CHANNEL_IN_STEREO
                , AudioFormat.ENCODING_PCM_16BIT
                , intBufferSize);

        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC
                , intRecordSampleRate
                , AudioFormat.CHANNEL_IN_STEREO
                , AudioFormat.ENCODING_PCM_16BIT
                , intBufferSize
                , AudioTrack.MODE_STREAM);

        audioTrack.setPlaybackRate(intRecordSampleRate);

        audioRecord.startRecording();
        audioTrack.play();

        while (isActive){
            audioRecord.read(shortAudioData, 0, shortAudioData.length);

            // Apply noise reduction
            double[] audioData = shortToDoubleArray(shortAudioData);
            double[] processedData = applyNoiseReduction(audioData);
            short[] processedAudio = doubleToShortArray(processedData);

            for (int i = 0; i < processedAudio.length; i++){
                processedAudio[i] = (short) Math.min (processedAudio[i] * intGain, Short.MAX_VALUE);
            }

            audioTrack.write(processedAudio, 0, processedAudio.length);
        }
    }

    private double[] shortToDoubleArray(short[] array) {
        double[] result = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] / 32768.0; // Convert to double range [-1.0, 1.0]
        }
        return result;
    }

    private short[] doubleToShortArray(double[] array) {
        short[] result = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = (short) (array[i] * 32768.0); // Convert to short range [-32768, 32767]
        }
        return result;
    }

    private double[] applyNoiseReduction(double[] audioData) {
        int frameSize = 1024;
        int hopSize = 256;
        double noiseThreshold = 0.2;

        DoubleFFT_1D fft = new DoubleFFT_1D(frameSize);
        int numFrames = (int) Math.ceil(audioData.length / (double) hopSize);
        double[] processedData = new double[audioData.length];

        for (int i = 0; i < numFrames; i++) {
            int start = i * hopSize;
            int end = Math.min(start + frameSize, audioData.length);

            double[] frame = new double[frameSize];
            System.arraycopy(audioData, start, frame, 0, end - start);

            fft.realForward(frame);

            for (int j = 0; j < frame.length / 2; j++) {
                double magnitude = Math.sqrt(frame[2 * j] * frame[2 * j] + frame[2 * j + 1] * frame[2 * j + 1]);
                double phase = Math.atan2(frame[2 * j + 1], frame[2 * j]);

                if (magnitude < noiseThreshold) {
                    frame[2 * j] = 0.0;
                    frame[2 * j + 1] = 0.0;
                }

                frame[2 * j] *= Math.cos(phase);
                frame[2 * j + 1] *= Math.sin(phase);
            }

            fft.realInverse(frame, true);

            for (int j = 0; j < frame.length; j++) {
                if (start + j < processedData.length) {
                    processedData[start + j] += frame[j] / frameSize;
                }
            }
        }

        return processedData;
    }
}
