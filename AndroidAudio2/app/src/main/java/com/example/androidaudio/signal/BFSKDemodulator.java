package com.example.androidaudio.signal;

import android.util.Log;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BFSKDemodulator {
   private final double fs = 48000;
   private final double fc;    // 载波频率
   private final double fd;    // 调制频率
   private final double symbolPeriod;  // 符号时间
   private final int N = 500;     // STFT窗口长度
   private final int d = 100;     // STFT窗口移动步长
   private final double f0, f1;    // 两个基带信号的频率
   private final byte preamble = 0b01010101;
   private final int preamble_c = 2;
   private final byte epilogue = (byte)0b11111111;
   private final int epilogue_c = 1;
   
   public BFSKDemodulator(double fc, double fd, double symbolPeriod) {
      this.fc = fc;
      this.fd = fd;
      this.symbolPeriod = symbolPeriod;
      this.f0 = fc - fd;
      this.f1 = fc + fd;
   }

    /*
     * DFT(离散傅里叶变换)
     * @param data
     * @param k
     */
    
    private Complex DFT(List<Double> data, int k) {
        Complex result = new Complex(0, 0);
        int N = data.size();
        for (int i = 0; i < N; i++) {
            Complex x_i = new Complex(data.get(i), 0);
            Complex e_pow = (new Complex(Math.E)).pow(new Complex(0, -2 * Math.PI * i * k / N));
            // sum of data[i]*e^(-2πik/N)
            result = result.add(x_i.multiply(e_pow));
        }
        return result;
    }

    /*
     * FT(傅里叶变换)
     * @param data 待分析的数据
     * @param f 分析的频率
     */
    private Complex FT(List<Double> data, double f) {
        int k = (int) Math.round(f * data.size() / fs);
        return DFT(data, k);
    }


    /*
     * STFT(Short Time Fourier Transform)短时傅里叶变换
     * 
     * @param fs
     * @param data
     */
    private ArrayList<List<Double>> STFT(double[] fs, double[] data) {
        ArrayList<List<Double>> result = new ArrayList<>();
        for (int i = 0; i < fs.length; i++) {
           result.add(new ArrayList<>());
        }
        List<Double> dataList = Arrays.stream(data).boxed().collect(Collectors.toList());
        for (int i = 0; i < data.length -d + 1; i += d){
            // select a window, length N
            List<Double> subData = dataList.subList(i, Math.min(i + N, data.length));
            // calculate the FT result of each frequency in fs
            for (int j = 0; j < fs.length; j++) {
                Complex complex = FT(subData, fs[j]);
                result.get(j).add(complex.abs());
            }
        }
        return result;
    }


    // trim the spectrum to remove the noise
    private List<List<Double>> trim_spectrum(List<List<Double>> X) {
        double[] energy = new double[X.get(0).size()];
        for (int j = 0;j < X.size();j++){
            List<Double> a = X.get(j);
            for (int i = 0;i < a.size();i++){
                energy[i] += a.get(i) * a.get(i);
            }
        }

        double max = 0;
        for (int i = 0;i < energy.length;i++){
            if (energy[i] > max) max = energy[i];
        }

        double threshold = max * 0.01;
        int st = IntStream.range(0, energy.length)
                .filter(v -> energy[v] > threshold)
                .findFirst()
                .orElse(-1);
        int ed = IntStream.range(0, energy.length)
                .map(v -> energy.length - 1 - v)
                .filter(v -> energy[v] > threshold)
                .findFirst()
                .orElse(-1);


        ArrayList<List<Double>> rtn = new ArrayList<>();
        for (int j = 0;j < X.size();j++) {
            if (st < 0 || ed < 0) rtn.add(new ArrayList<>());
            else rtn.add(X.get(j).subList(st, ed));
        }
        return rtn;
    }

    // turn the data sequence into a binary sequence by thresholding
    private List<Double> thresholding(List<List<Double>> data) {
        List<Double> e0 = data.get(0);
        List<Double> e1 = data.get(1);
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < e0.size(); i++){
            // thresholding by binarization function v=1/(1+e^(-30*(e0-e1)))
            double v = 1 / (1 + FastMath.pow(e1.get(i) / e0.get(i), -30));
            result.add(v);
        }
        return result;
    }

    // find the preamble in the data
    private int code_at(List<Double> data, int l, int r){
        int high = 0;
        int low = 0;
        for (int i = l; i < r; i++){
            if (i >= data.size()){
                break;
            }
            else if (data.get(i) > 0.9){
                high++;
            }
            else if (data.get(i) < 0.1){
                low++;
            }
        }
        return high > low ? 1 : 0;
    }


    // count the number of 1s in a byte
    private int count_ones(int x) {
        int c = 0;
        for(int i = 0;i < 8;i++){
            c += ((x>>i) & 1);
        }
        return c;
    }

    // public byte[] demodulate(double[] data) {
    //     double[] fs = {f0, f1};
    //     ArrayList<List<Double>> sequence = STFT(fs, data);
    //     sequence = trim_sequence(sequence);
    //     List<Double> binary_sequence = thresholding(sequence);
    //     List<Integer> result = new ArrayList<>();
    //     for (int i = 0; i < binary_sequence.size(); i += 8) {
    //         int code = 0;
    //         for (int j = 0; j < 8; j++) {
    //             code = code * 2 + code_at(binary_sequence, i + j, i + j + 1);
    //         }
    //         if (count_ones(code) % 2 == 0) {
    //             result.add(code);
    //         }
    //     }
    //     byte[] bytes = new byte[result.size()];
    //     for (int i = 0; i < result.size(); i++) {
    //         bytes[i] = (byte) result.get(i);
    //     }
    //     return bytes;
    // }
     public DecodeResult getData(double[] signal) {
        Log.d("BFSK", "f0: " + f0);
        Log.d("BFSK", "f1: " + f1);

        List<List<Double>> X = STFT(new double[]{f0, f1}, signal);
        X = trim_spectrum(X);
        List<Double> val = thresholding(X);

        List<Integer> code = new ArrayList<>();
        for (int i = 0; true; i++) {
            int l = (int) Math.round(i * symbolPeriod * fs / d);
            int r = (int) Math.round((i+1) * symbolPeriod * fs / d);
            if(l > val.size()) break;
            int c = code_at(val, l, r);
            code.add(c);
        }

        Log.d("BFSK", "Decode raw: " + code.toString());

        ArrayList<Integer> msg = new ArrayList<>();
        int msg_len = (int)Math.ceil(code.size() / 8.0);
        msg.ensureCapacity(msg_len);
        for(int i = 0;i < msg_len;i++){
            int b = 0;
            for(int j = 0;j < 8;j++){
                int k = i * 8 + j;
                int bit = k < code.size() ? code.get(k) : 1;
                b |= (bit<<j);
            }
            msg.add(b);
        }
        Log.d("BFSK", "Decode msg: " + msg.toString());

        int error_bit = 0;
        if(msg.size() < preamble_c + epilogue_c) {
            return new DecodeResult(new byte[]{}, 1,new String("Message too short"));
        }

        for(int i = 0;i < preamble_c;i++){
            int e = msg.get(i) ^ preamble;
            error_bit += count_ones(e);
        }

        for(int j = msg.size() - epilogue_c;j < msg.size();j++){
            int e = msg.get(j) ^ epilogue;
            error_bit += count_ones(e);
        }

        byte[] bytes = new byte[msg.size() - preamble_c - epilogue_c];
        for (int i = 0;i < bytes.length;i++){
            bytes[i] = msg.get(i + preamble_c).byteValue();
        }

        return new DecodeResult(
                bytes,
                error_bit / 8.0 / (preamble_c + epilogue_c),
                code.toString()
        );
    }

    public static class DecodeResult {
        public byte[] msg;
        public double error;
        public String code;

        public DecodeResult(byte[] msg, double error, String code) {
            this.msg = msg;
            this.error = error;
            this.code = code;
        }
    }
}
