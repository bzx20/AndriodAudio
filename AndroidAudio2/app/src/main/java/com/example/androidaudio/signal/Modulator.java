package com.example.androidaudio.signal;

import org.apache.commons.math3.complex.Complex;

public interface Modulator {
    /**
     * 获取调制的实信号（已上载波）
     * @param carrierFrequency 载波频率
     * @param dataToModulate 待调制的数据
     * @return 调制的实信号
     */
    public default double[] getRealSignal(double carrierFrequency, byte[] dataToModulate){
        return new double[0];
    }

    /**
     * 获取IQ基带信号
     * @param dataToModulate
     * @return
     */
    public default Complex[] getBaseBand(byte[] dataToModulate){
        return new Complex[0];
    }

}
