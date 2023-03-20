package com.example.androidaudio;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.androidaudio.audio.AudioUtils;
import com.example.androidaudio.signal.BFSKModulator;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {
    private final static int REQUEST_PERMISSION_CODE = 1;
    private final int SAMPLE_RATE = 48000;
    private final int BUFFER_SIZE = 4800;
    private boolean recordStatus = false;
    private boolean playStatus = false;

    private final String PCM_FILE_NAME = "record.pcm";
    private final String WAVE_FILE_NAME = "record.wav";

    // @BindView(R.id.record_button)
    // Button recordButton;
    @BindView(R.id.play_button)
    Button playButton;
    @BindView(R.id.edit_text_encode_data)
    EditText editEncodeData;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        // 检查并请求录音权限
        // if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        //     recordButton.setEnabled(false);
        //     ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSION_CODE);
        // }

    }

    public void onPlayButtonClicked(View view) {
        if (!playStatus) {
            playStatus = true;
            // 检查是不是有已录制的文件
//            File pcmFile = new File(this.getCacheDir(), PCM_FILE_NAME);
//            if (!pcmFile.exists()) {
//                Toast.makeText(this, R.string.play_error_help, Toast.LENGTH_SHORT).show();
//                return;
//            }
            playButton.setEnabled(false);
            Toast.makeText(this, R.string.start_play_help, Toast.LENGTH_SHORT).show();


            int bufferSize = AudioTrack.getMinBufferSize(AudioUtils.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            Log.d("Play", "bufferSize:" + bufferSize);

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
            // 读取BFSK的参数
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            double carrierFrequency = Double.parseDouble(pref.getString("carrier_frequency", "6000"));
            double frequencyDeviation = Double.parseDouble(pref.getString("fsk_frequency_deviation", "1000"));
            double symbolPeriod = Double.parseDouble(pref.getString("fsk_symbol_period", "0.1"));
            // 创建调制器
            BFSKModulator bfskModulation = new BFSKModulator(AudioUtils.SAMPLE_RATE, frequencyDeviation, symbolPeriod);
            byte[] dataToModulate = editEncodeData.getText().toString().getBytes();

            Log.d("BFSK 参数", carrierFrequency + " " + frequencyDeviation + " " + symbolPeriod);

            final File cacheDir = getCacheDir();

            // 开始生成音频信号并播放
            Thread playThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        final byte[] buffer = new byte[bufferSize];
                        // 获取调制后的信号
                        double[] signal = bfskModulation.getRealSignal(carrierFrequency, dataToModulate);
                        // 转化为PCM编码的字节流
                        byte[] pcmData = AudioUtils.doubleToPCM(signal);
                        // 转为输入流
                        InputStream is = new ByteArrayInputStream(pcmData);

                        // 保存源文件到硬盘
                        File rawPCMFile = new File(cacheDir, "raw.pcm");
                        File rawWAVFile = new File(cacheDir, "raw.wav");
                        try (FileOutputStream fos = new FileOutputStream(rawPCMFile)){
                            fos.write(pcmData);
                        }
                        AudioUtils.PCMToWAV(rawPCMFile, rawWAVFile, 1, AudioUtils.SAMPLE_RATE, 16);
                        File signalFile = new File(cacheDir, "sig.txt");
                        try(
                                FileWriter w = new FileWriter(signalFile);
                                ){
                            for(double b : signal) w.write(b + "\n");
                        }

                        while (is.available() > 0 && playStatus) {
                            int readCount = is.read(buffer);
                            Log.d("PLay", "读取数据" + readCount);
                            if (readCount == -1) {
                                break;
                            }
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
                                playButton.setEnabled(true);
                            }
                        });
                        Log.d("PLay", "播放完成");
                        playStatus = false;
                        audioTrack.stop();
                        audioTrack.release();
                    }

                }
            });
            playThread.start();
        }

    }

    public void onStopButtonClicked(View view) {
        playStatus = false;
        playButton.setEnabled(true);
    }
    
    public void onGotoDemodBtnClicked(View view) {
        Intent intent = new Intent(this, DemodActivity.class);
        startActivity(intent);
    }

    // public void onRecordButtonClicked(View view) throws IOException {
    //     // 切换录音状态
    //     Button recordButton = (Button) findViewById(R.id.record_button);
    //     if (!recordStatus) {
    //         recordStatus = true;
    //         recordButton.setText(R.string.stop_record);
    //         playButton.setEnabled(false);
    //         Toast.makeText(this, R.string.start_record_help, Toast.LENGTH_SHORT).show();
    //     } else {
    //         recordStatus = false;
    //         recordButton.setText(R.string.record);
    //         playButton.setEnabled(true);
    //         Toast.makeText(this, R.string.stop_record_help, Toast.LENGTH_SHORT).show();
    //         return;
    //     }

    //     File pcmFile = new File(this.getCacheDir(), PCM_FILE_NAME);
    //     if (pcmFile.exists()) {
    //         Log.d("Record", "删除已有的pcmFile");
    //         pcmFile.delete();
    //     }

    //     File waveFile = new File(this.getCacheDir(), WAVE_FILE_NAME);
    //     if (waveFile.exists()) {
    //         Log.d("Record", "删除已有的waveFile");
    //         waveFile.delete();
    //     }

    //     DataOutputStream dos = new DataOutputStream(new FileOutputStream(pcmFile));

    //     if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
    //         // TODO: Consider calling
    //         //    ActivityCompat#requestPermissions
    //         // here to request the missing permissions, and then overriding
    //         //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
    //         //                                          int[] grantResults)
    //         // to handle the case where the user grants the permission. See the documentation
    //         // for ActivityCompat#requestPermissions for more details.
    //         return;
    //     }
    //     int bufferSize = AudioRecord.getMinBufferSize(AudioUtils.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    //     Log.d("Record", "bufferSize:" + bufferSize);
    //     final AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

    //     Thread recordThread = new Thread(new Runnable() {
    //         @Override
    //         public void run() {

    //             final byte[] buffer = new byte[bufferSize];
    //             audioRecord.startRecording();
    //             try {
    //                 while (recordStatus) {
    //                     int samplesRead = audioRecord.read(buffer, 0, bufferSize);
    //                     if (samplesRead != bufferSize) {
    //                         Log.w("AudioUtils", "Samples read not equal minSize (" + samplesRead + "). Might be loosing data!");
    //                     }
                       
    //                     dos.write(buffer, 0, samplesRead);
    //                 }

    //                 audioRecord.stop();
    //                 audioRecord.release();

    //                 dos.close();
    //                 AudioUtils.PCMToWAV(pcmFile, waveFile, 1, AudioUtils.SAMPLE_RATE, 16);
    //             } catch (IOException e) {
    //                 e.printStackTrace();
    //             }
    //         }
    //     });
    //     recordThread.start();

    // }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setting:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // @Override
    // public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    //     switch (requestCode) {
    //         case REQUEST_PERMISSION_CODE:
    //             if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //                 recordButton.setEnabled(true);
    //             }
    //     }
    //     super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    // }
}