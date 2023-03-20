package com.example.androidaudio;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.androidaudio.audio.AudioUtils;
import com.example.androidaudio.signal.BFSKDemodulator;
import com.example.androidaudio.signal.BFSKModulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;


public class DemodActivity extends AppCompatActivity {
    private static final String PCM_FILE_NAME = "record.pcm";
    private static final String WAVE_FILE_NAME = "record.wav";
    private static final int SAMPLE_RATE = 48000;

    @BindView(R.id.record_btn)
    Button recordBtn;
    @BindView(R.id.stop_btn)
    Button stopBtn;
    @BindView(R.id.play_btn)
    Button playBtn;
    @BindView(R.id.decode_btn)
    Button decodeBtn;
    @BindView(R.id.decode_raw_btn)
    Button decoderawBtn;// Decode raw pcm file
    @BindView(R.id.error_text)
    TextView error_text;
    @BindView(R.id.text_view)
    TextView textView;
    @BindView(R.id.text_view_code)
    TextView textViewCode;

    private Boolean recordStatus = false;
    private Boolean playStatus = false;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demod);
        ButterKnife.bind(this);

        // Check for permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

    }

    public void onGotoTxBtnClicked(View view) {
        Intent intent = new Intent();
        intent.setClass(this, MainActivity.class);
        startActivity(intent);
    }

    public void onRecordBtnClicked(View view) throws FileNotFoundException {
        if (recordStatus) return;

        recordStatus = true;
        recordBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        playBtn.setEnabled(false);
        decodeBtn.setEnabled(false);
        decoderawBtn.setEnabled(false);
        Toast.makeText(this, R.string.start_record_help, Toast.LENGTH_SHORT).show();


        File pcmFile = new File(this.getCacheDir(), PCM_FILE_NAME);
        Log.i("AudioUnit", "PCM file " + pcmFile.getAbsolutePath());
        if (pcmFile.exists()) {
            pcmFile.delete();
        }

        File waveFile = new File(this.getCacheDir(), WAVE_FILE_NAME);
        if (waveFile.exists()) {
            waveFile.delete();
        }

        DataOutputStream dos = new DataOutputStream(new FileOutputStream(pcmFile));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        int bufferSize = AudioRecord.getMinBufferSize(
                AudioUtils.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        Log.d("Record", "bufferSize:" + bufferSize);
        final AudioRecord audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );

        Context that = this;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        double fc = Double.parseDouble(pref.getString("carrier_frequency", "6000"));
        double fd = Double.parseDouble(pref.getString("fsk_frequency_deviation", "1000"));
        double ts = Double.parseDouble(pref.getString("fsk_symbol_period", "0.1"));
        Thread recordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final byte[] buffer = new byte[bufferSize];
                audioRecord.startRecording();
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    while (recordStatus) {
                        int samplesRead = audioRecord.read(buffer, 0, bufferSize);
                        if (samplesRead != bufferSize) {
                            Log.w("AudioUtils", "Samples read not equal minSize (" + samplesRead + "). Might be loosing data!");
                        }

                        dos.write(buffer, 0, samplesRead);
                        bos.write(buffer, 0, samplesRead);
                    }

                    audioRecord.stop();
                    audioRecord.release();

                    dos.close();
                    AudioUtils.PCMToWAV(pcmFile, waveFile, 1, AudioUtils.SAMPLE_RATE, 16);

                    byte[] pcmData = bos.toByteArray();

                    double[] sig = AudioUtils.PCMToDouble(pcmData);

                    String name = "recsig_"+((int)fc)+"_"+((int)fd)+"_"+ts+".txt";
                    try(FileWriter sw = new FileWriter(new File(that.getCacheDir(), name))){
                        for(double b : sig){
                            sw.write(b + "\n");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally { // Make sure we always stop recording
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            recordStatus = false;
                            recordBtn.setEnabled(true);
                            stopBtn.setEnabled(false);
                            playBtn.setEnabled(true);
                            decodeBtn.setEnabled(true);
                            decoderawBtn.setEnabled(true);
                            Toast.makeText(that, "结束录制", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
        recordThread.start();
    }

    public void onStopBtnClicked(View view) {
        recordStatus = false;
    }

    public void onPlayBtnClicked(View view) {
        if (playStatus) {
            playStatus = false;
            return;
        }

        // 检查是不是有已录制的文件
        File pcmFile = new File(this.getCacheDir(), PCM_FILE_NAME);
        if (!pcmFile.exists()) {
            Toast.makeText(this, R.string.play_error_help, Toast.LENGTH_SHORT).show();
            return;
        }
        // calculate the minimum buffer size
        int bufferSize = AudioTrack.getMinBufferSize(
                AudioUtils.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        // create an audiotrack object
        AudioTrack audioTrack = new AudioTrack(
                new AudioAttributes.Builder().
                        setUsage(AudioAttributes.USAGE_MEDIA).
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).
                        build(),
                new AudioFormat.Builder().
                        setChannelMask(AudioFormat.CHANNEL_OUT_MONO).
                        setEncoding(AudioFormat.ENCODING_PCM_16BIT).
                        setSampleRate(AudioUtils.SAMPLE_RATE).
                        build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        audioTrack.play();

        playStatus = true;
        playBtn.setText("停止播放");
        Toast.makeText(this, R.string.start_play_help, Toast.LENGTH_SHORT).show();

        File cacheDir = this.getCacheDir();
        // 开始生成音频信号并播放
        Thread playThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final byte[] buffer = new byte[bufferSize];
                    File pcmFile = new File(cacheDir, PCM_FILE_NAME);
                    FileInputStream fin = new FileInputStream(pcmFile);

                    while (fin.available() > 0 && playStatus) {
                        int readCount = fin.read(buffer);
                        if (readCount == -1) break;
                        int writeResult = audioTrack.write(buffer, 0, readCount);
                        if (writeResult < 0) {
                            continue;
                        }
                    }
                } catch (IOException e) {
                    Log.e("PLay", "发生IO错误");
                    e.printStackTrace();
                } finally {
                    // play完成，恢复playButton状态
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playStatus = false;
                            playBtn.setText("播放");
                        }
                    });
                    audioTrack.stop();
                    audioTrack.release();
                }

            }
        });
        playThread.start();
    }

    public void onDecodeBtnClicked(View view) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        double carrierFrequency = Double.parseDouble(pref.getString("carrier_frequency", "6000"));
        double frequencyDeviation = Double.parseDouble(pref.getString("fsk_frequency_deviation", "1000"));
        double symbolPeriod = Double.parseDouble(pref.getString("fsk_symbol_period", "0.1"));
        BFSKDemodulator demodulator = new BFSKDemodulator(
                carrierFrequency,
                frequencyDeviation,
                symbolPeriod
        );

        File pcmFile = new File(getCacheDir(), PCM_FILE_NAME);
        if (!pcmFile.exists()) {
            textView.setText("record something first..");
            return;
        }
        decodeBtn.setEnabled(false);

        DemodActivity that = this;
        Thread decodeThread = new Thread(() -> {
            double[] signal = null;
            String ioerr = null;
            try {
                byte[] pcmData = Files.readAllBytes(pcmFile.toPath());
                signal = AudioUtils.PCMToDouble(pcmData);
            } catch (IOException e) {
                ioerr = e.getMessage();
            }

            double error = 0;
            String msg = "null";
            String code = "null";
            if (ioerr == null) {
                BFSKDemodulator.DecodeResult rst = demodulator.getData(signal);

                // a little post postprocess
                int len = rst.msg.length - 1;
                for(; len >= 0;len--) {
                    if(rst.msg[len] != -1) break;
                }
                if (len == -1) len = rst.msg.length;
                else len += 1;

                msg = new String(rst.msg, 0, len);
                Log.d("DemodActivity", "decode result: " + msg);
                Log.d("DemodActivity", "decode raw" + Arrays.toString(rst.msg));
                error = rst.error;
                code = rst.code;
            } else {
                msg = ioerr;
            }

            that.setDecodeRst(msg, error, code);
        });
        decodeThread.start();
    }

    // Decode the raw pcm data
    public void onDecodeRawBtnClicked(View view) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        double carrierFrequency = Double.parseDouble(pref.getString("carrier_frequency", "6000"));
        double frequencyDeviation = Double.parseDouble(pref.getString("fsk_frequency_deviation", "1000"));
        double symbolPeriod = Double.parseDouble(pref.getString("fsk_symbol_period", "0.1"));
        BFSKDemodulator demodulator = new BFSKDemodulator(
                carrierFrequency,
                frequencyDeviation,
                symbolPeriod
        );

        File pcmFile = new File(getCacheDir(), "raw.pcm");
        if (!pcmFile.exists()) {
            textView.setText("record something first..");
            return;
        }
        decodeBtn.setEnabled(false);

        DemodActivity that = this;
        Thread decodeThread = new Thread(() -> {
            double[] signal = null;
            String ioerr = null;
            try {
                byte[] pcmData = Files.readAllBytes(pcmFile.toPath());
                signal = AudioUtils.PCMToDouble(pcmData);
            } catch (IOException e) {
                ioerr = e.getMessage();
            }

            double error = 0;
            String msg = "null";
            String code = "null";
            if (ioerr == null) {
                BFSKDemodulator.DecodeResult rst = demodulator.getData(signal);

                // a little post postprocess
                int len = rst.msg.length - 1;
                for(; len >= 0;len--) {
                    if(rst.msg[len] != -1) break;
                }
                if (len == -1) len = rst.msg.length;
                else len += 1;

                msg = new String(rst.msg, 0, len);
                Log.d("DemodActivity", "decode result: " + msg);
                Log.d("DemodActivity", "decode raw" + Arrays.toString(rst.msg));
                error = rst.error;
                code = rst.code;
            } else {
                msg = ioerr;
            }

            that.setDecodeRst(msg, error, code);
        });
        decodeThread.start();
    }

    public void setDecodeRst(final String msg, double err, String code) {
        this.runOnUiThread(() -> {
            decodeBtn.setEnabled(true);
            textView.setText(msg);
            error_text.setText(String.format("%.3f", err));
            textViewCode.setText(code);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    recordBtn.setEnabled(true);
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}


