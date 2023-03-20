# Android Audio开发文档

[TOC]

## 1. 实现功能

– 能够输入特定文本字符串，将其信息编码调制到声波信号上，并使用扬声器将其播放出去。也可以直接解码生成的录音文件。

– 可以通过麦克风录制一段音频信号，之后执行特定的信号处理操作，最终将其中的信息解码并显示到UI界面上。

– 可以设置和修改参数，如声波的载波频率、调制的频偏、符号周期等。

## 2. 界面设计与功能实现

<img src="C:\Users\bzx2021\AppData\Roaming\Typora\typora-user-images\image-20230110182728028.png" alt="image-20230110182728028" style="zoom: 50%;" />

### 2.1 Activity

1. `MainActivity`

   主界面，发送编码数据，播放/停止，切换到其他两个activity。

2. `DemodActivity`

   解码界面，录制/播放/停止，解码，跳转到主界面发送端。

3. `SettingActivity`

   设置界面，更改载波频率、调制频偏、符号周期等。

### 2.2 音频播放与多线程

#### 2.2.1 目标功能

– 音频权限动态申请 

– 使用多线程在后台录制、播放音频 

– 可以从前台控制播放，录制的状态

#### 2.2.2 `AudioRecord`类

获取权限、创建一个线程用来录音

#### 2.2.3 `AudioTrack`类 

将PCM音频缓冲区流式传输到音频接收器播放

创建一个线程播放

## 3. 算法实现

### BFSK频率调制

##### 二进制FSK（BFSK）的相关参数

1. 用来调制比特0和比特1的信号频率$f_0$和$f_1$
2. 载波频率$f_c=\frac{f_0+f_1}{2}$
3. 频偏$\Delta f =f_c-f_0$
4. 符号时间$sp$
5. 符号采样点数$sps$

### 3.1 DFT 离散傅里叶变换

$$
Y(2\pi f_0)=\Sigma^{sps}_{n=0}y[n]*e^{-2j\pi f_0}
$$

$$
Y(2\pi f_1)=\Sigma^{sps}_{n=0}y[n]*e^{-2j\pi f_1}
$$

```java
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
```

### 3.2 STFT基本理论

傅里叶变换只反映出信号在频域的特性，无法在时域内对信号进行分析。为了将时域和频域相联系，Gabor于1946年提出了短时傅里叶变换(short-time Fourier transform，STFT)，其实质是加窗的傅里叶变换。STFT的过程是：在信号做傅里叶变换之前乘一个时间有限的窗函数 $h(t)$，并假定非平稳信号在分析窗的短时间隔内是平稳的，通过窗函数 $h(t)$ 在时间轴上的移动，对信号进行逐段分析得到信号的一组局部“频谱”。信号的短时傅里叶变换定义为：
$$
STFT(t,f)=\int^{\infty}_{-\infty}x(\tau)h(\tau-t)e^{-j2\pi f\tau}d\tau
$$
其中$h(\tau-t)$为分析窗函数。

由上式知，信号在时间 t 处的短时傅里叶变换就是信号乘上一个以 t 为中心的“分析窗”后所作的傅里叶变换。乘以分析窗函数等价于取出信号在分析时间点 t 附近的一个切片。对于给定时间 t，可以看作是该时刻的频谱。特别是，当窗函数取时，则短时傅里叶变换就退化为传统的傅里叶变换。要得到最优的局部化性能，时频分析中窗函数的宽度应根据信号特点进行调整，即正弦类信号用大窗宽，脉冲型信号用小窗宽。

### 3.3 为什么使用STFT

当信号在采集到的 N 个采样点内没有振动整数个周期的时候，做DFT会出现Frequency Leakage的问题，这会为根据频谱重建原信号带来问题，为了减少Frequency Leakage的影响，可以对原信号乘以一个窗函数(从0开始到0结束)之后做DFT。

然而直接对一个非平稳信号做DFT无法得到信号变化的时序信息，例如在一段时间内，有很多信号先后出现后消失，直接做DFT无法判断出不同信号出现的先后顺序，而STFT通过每次取出信号中的一小段加窗后做DFT来反映信号随时间的变化。

在实际解码中，我使用矩形窗做短时傅里叶变换对音频信号处理，代码如下：

```java
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

```

## 4. 性能分析

### 4.1 测试环境

Android手机端发送音频信号，PC端`AndoridStudio`模拟器接收并解码。

### 4.2 测试结果及误码率分析

| 序号 | 输入序列   | 载波频率$f_c$ | 调制频偏$\Delta f$ | 解码结果   | 误码率 Error |
| :--: | :--------- | :-----------: | :----------------: | :--------- | :----------: |
|  1   | HelloWorld |     5000      |        1000        | HelloWorld |    0.000     |
|  2   | 123456     |     5000      |        1000        | 123456     |    0.012     |
|  3   | Hello1234  |     4000      |        1000        | Hello1234  |    0.052     |
|  4   | Hello1234  |     5000      |        1000        | Hello1234  |    0.040     |
|  5   | Hello1234  |     6000      |        1000        | Hello1234  |    0.042     |
|  6   | Hello1234  |     5000      |        500         | Hello1234  |    0.050     |
|  7   | aAbBcCdD   |     5000      |        500         | aAbBcCdD   |    0.022     |
|  8   | aAbBcCdD   |     4000      |        1000        | aAbBcCdD   |    0.032     |
|  9   | aAbBcCdD   |     6000      |        1000        | aAbBcCdD   |    0.024     |


误码率：正常测试环境基本在0.05以下；手机录音机录制后播放噪声干扰较强，音频失真，误码率在0.3-0.5左右；出现`???`解码结果。

更改载波频率和频偏分析误码率，结果差异较为不显著。

## 5. 重难点问题与解决

实际开发中遇到的问题，主要在以下方面：

1. Android Studio 开发模块较多，对于局部函数调试不友好，可以对函数进行单独测试后集成。
2. Android Studio 仿真模拟器麦克风录制效果不稳定，有时模拟器会崩溃需要重启。
3. 解码时需要对信号开始、结束进行识别，主要使用识别音频强度超过指定阈值来识别，加入前导信号提高准确性。

## 6. 应用发布

开发环境：Android Studio 2021.3.1

发布版本：使用目录下 `app-release.apk` 安装
