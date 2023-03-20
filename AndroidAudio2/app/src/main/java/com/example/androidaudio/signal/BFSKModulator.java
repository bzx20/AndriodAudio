package com.example.androidaudio.signal;

import androidx.annotation.NonNull;

import java.nio.DoubleBuffer;

public class BFSKModulator implements Modulator {
    private final double sampleRate;
    private final double frequencyDeviation;
    private final double symbolPeriod;

    public BFSKModulator(double sampleRate, double frequencyDeviation, double symbolPeriod) {
        this.sampleRate = sampleRate;
        this.frequencyDeviation = frequencyDeviation;
        this.symbolPeriod = symbolPeriod;
    }

    /**
     * 获取调制的实数信号
     * @param carrierFrequency 载波频率
     * @param dataToModulate 待调制的数据，其数据类型为byte[]，一个byte对应8个bit(符号)
     * @return 调制出的实数信号
     */
    @Override
    public double[] getRealSignal(double carrierFrequency, @NonNull byte[] dataToModulate) {
        final int preambleLength = 2;
        final byte premable = 0b01010101;
        final int epilogueLegnth = 1;
        final byte epilogue = (byte) 0b11111111;

        int samplesPerSymbol = Math.toIntExact(Math.round(symbolPeriod * sampleRate));
        DoubleBuffer doubles = DoubleBuffer.allocate(
                (dataToModulate.length + preambleLength + epilogueLegnth)
                        * Byte.SIZE * samplesPerSymbol
        );
        double f0 = carrierFrequency - this.frequencyDeviation;
        double f1 = carrierFrequency + this.frequencyDeviation;

        for(int k = 0;k < preambleLength;k++) {
            byte d = premable;
            for (int i = 0; i < 8; i++) {
                int b = ((d >> i) & 1);
                double f = b == 1 ? f1 : f0;

                for (int j = 0; j < samplesPerSymbol; j++) {
                    doubles.put(Math.cos(2 * Math.PI * f * j / sampleRate));
                }
            }
        }

        for(byte d : dataToModulate) {
            for (int i = 0;i < 8;i++){
                int b = ((d>>i) & 1);
                double f = b == 1 ? f1 : f0;

                for (int j = 0;j < samplesPerSymbol;j++) {
                    doubles.put(Math.cos(2 * Math.PI * f * j / sampleRate));
                }
            }
        }

        for(int k = 0;k < epilogueLegnth;k++) {
            byte d = epilogue;
            for (int i = 0; i < 8; i++) {
                int b = ((d >> i) & 1);
                double f = b == 1 ? f1 : f0;

                for (int j = 0; j < samplesPerSymbol; j++) {
                    doubles.put(Math.cos(2 * Math.PI * f * j / sampleRate));
                }
            }
        }

        return doubles.array();
    }
//        int samplesPerSymbol = Math.toIntExact(Math.round(symbolPeriod * sampleRate));
//        DoubleBuffer doubles = DoubleBuffer.allocate(dataToModulate.length * Byte.SIZE * samplesPerSymbol);
//        // TODO: 根据相关参数生成BFSK的信号
//        double f_0 = carrierFrequency - frequencyDeviation;
//        double f_1 = carrierFrequency + frequencyDeviation;
//        for (int i = 0; i < dataToModulate.length; i++) {
//           for (int j = 0; j < Byte.SIZE; j++) {
//               if (dataToModulate[i]==1) {
//                   for (int k = 0; k < samplesPerSymbol; k++) {
//                       doubles.put(Math.sin(2 * Math.PI * f_1 * k / sampleRate));
//                   }
//               } else {
//                   for (int k = 0; k < samplesPerSymbol; k++) {
//                       doubles.put(Math.sin(2 * Math.PI * f_0 * k / sampleRate));
//                   }
//               }
//           }
//        }
//
//        return doubles.array();
//    }
}
