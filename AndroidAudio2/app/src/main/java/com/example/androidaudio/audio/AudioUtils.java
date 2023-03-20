package com.example.androidaudio.audio;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

public class AudioUtils {
    public final static int SAMPLE_RATE = 48000;


    /**
     * 将double[]数组转化为PCM字节流
     * @param audioData
     * @return
     */
    static public byte[] doubleToPCM(double[] audioData) {
        short shorts[] = new short[audioData.length];
        for (int i = 0; i < audioData.length; i++) {
            if (Math.abs(audioData[i]) <= 1) {
                shorts[i] = (short) Math.round(audioData[i] * Short.MAX_VALUE);
            } else if (audioData[i] > 1) { // 超出范围的进行截断
                shorts[i] = Short.MAX_VALUE;
            } else if (audioData[i] < 1) {
                shorts[i] = Short.MIN_VALUE;
            }
        }
        ByteBuffer bytes = ByteBuffer.allocate(shorts.length * 2);
        for (short s : shorts) {
            bytes.putShort(s);
        }
        return bytes.array();
    }

    /**
     * 将PCM字节流转化为double[]
     * @param pcmData
     * @return
     */
    static public double[] PCMToDouble(byte[] pcmData) {
        short shorts[] = new short[pcmData.length / 2];
        // ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts, 0, shorts.length);
        DoubleBuffer doubles = DoubleBuffer.allocate(shorts.length);
        for (short s : shorts) {
            doubles.put(s / 32767.0);
        }
        return doubles.array();
    }


    /**
     * @param input         raw PCM data
     *                      limit of file size for wave file: < 2^(2*4) - 36 bytes (~4GB)
     * @param output        file to encode to in wav format
     * @param channelCount  number of channels: 1 for mono, 2 for stereo, etc.
     * @param sampleRate    sample rate of PCM audio
     * @param bitsPerSample bits per sample, i.e. 16 for PCM16
     * @throws IOException in event of an error between input/output files
     * @see <a href="http://soundfile.sapp.org/doc/WaveFormat/">soundfile.sapp.org/doc/WaveFormat</a>
     */
    static public void PCMToWAV(File input, File output, int channelCount, int sampleRate, int bitsPerSample) {
        final int inputSize = (int) input.length();

        try {
            OutputStream encoded = new FileOutputStream(output);
            // WAVE RIFF header
            writeToOutput(encoded, "RIFF"); // chunk id
            writeToOutput(encoded, 36 + inputSize); // chunk size
            writeToOutput(encoded, "WAVE"); // format

            // SUB CHUNK 1 (FORMAT)
            writeToOutput(encoded, "fmt "); // subchunk 1 id
            writeToOutput(encoded, 16); // subchunk 1 size
            writeToOutput(encoded, (short) 1); // audio format (1 = PCM)
            writeToOutput(encoded, (short) channelCount); // number of channelCount
            writeToOutput(encoded, sampleRate); // sample rate
            writeToOutput(encoded, sampleRate * channelCount * bitsPerSample / 8); // byte rate
            writeToOutput(encoded, (short) (channelCount * bitsPerSample / 8)); // block align
            writeToOutput(encoded, (short) bitsPerSample); // bits per sample

            // SUB CHUNK 2 (AUDIO DATA)
            writeToOutput(encoded, "data"); // subchunk 2 id
            writeToOutput(encoded, inputSize); // subchunk 2 size
            copy(new FileInputStream(input), encoded);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Size of buffer used for transfer, by default
     */
    private static final int TRANSFER_BUFFER_SIZE = 10 * 1024;

    /**
     * Writes string in big endian form to an output stream
     *
     * @param output stream
     * @param data   string
     * @throws IOException
     */
    public static void writeToOutput(OutputStream output, String data) throws IOException {
        for (int i = 0; i < data.length(); i++)
            output.write(data.charAt(i));
    }

    public static void writeToOutput(OutputStream output, int data) throws IOException {
        output.write(data >> 0);
        output.write(data >> 8);
        output.write(data >> 16);
        output.write(data >> 24);
    }

    public static void writeToOutput(OutputStream output, short data) throws IOException {
        output.write(data >> 0);
        output.write(data >> 8);
    }

    public static long copy(InputStream source, OutputStream output)
            throws IOException {
        return copy(source, output, TRANSFER_BUFFER_SIZE);
    }

    public static long copy(InputStream source, OutputStream output, int bufferSize) throws IOException {
        long read = 0L;
        byte[] buffer = new byte[bufferSize];
        for (int n; (n = source.read(buffer)) != -1; read += n) {
            output.write(buffer, 0, n);
        }
        return read;
    }
}
