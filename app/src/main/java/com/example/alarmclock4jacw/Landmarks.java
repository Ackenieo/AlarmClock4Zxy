package com.example.alarmclock4jacw;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

/**
 * 33 个骨骼点的归一化坐标（0~1，相对图像宽高）。
 *
 * 为什么要单独抽出来：ML Kit 的 {@code PoseLandmark.getPosition()} 返回的是**图像像素坐标**，
 * 不是 0~1 归一化坐标。如果直接拿去做比例计算或画图，会得到错得离谱的结果
 * （画出来全部跑到屏幕外）。这里统一在入口处按图像尺寸归一化一次，
 * 后面的计数逻辑和绘制就都只跟比例打交道，离镜头远近、分辨率高低都不影响。
 */
public class Landmarks {

    public static final int COUNT = 33;

    public final float[] x = new float[COUNT];
    public final float[] y = new float[COUNT];
    public final boolean[] visible = new boolean[COUNT];
    /** 全部关键点都不可用时为 false */
    public boolean any;

    /** 关键点可信度下限 */
    public static final float LIKELIHOOD_MIN = 0.35f;

    public static Landmarks fromPose(Pose pose, int imageWidth, int imageHeight) {
        Landmarks lm = new Landmarks();
        if (pose == null) return lm;

        // 自动识别坐标空间：只要有一个坐标超过 1.5，就说明是像素坐标
        boolean pixelSpace = false;
        for (int i = 0; i < COUNT; i++) {
            PoseLandmark p = pose.getPoseLandmark(i);
            if (p != null && (p.getPosition().x > 1.5f || p.getPosition().y > 1.5f)) {
                pixelSpace = true;
                break;
            }
        }
        float sx = pixelSpace && imageWidth > 0 ? 1f / imageWidth : 1f;
        float sy = pixelSpace && imageHeight > 0 ? 1f / imageHeight : 1f;

        for (int i = 0; i < COUNT; i++) {
            PoseLandmark p = pose.getPoseLandmark(i);
            if (p == null || p.getInFrameLikelihood() < LIKELIHOOD_MIN) continue;
            lm.x[i] = p.getPosition().x * sx;
            lm.y[i] = p.getPosition().y * sy;
            lm.visible[i] = true;
            lm.any = true;
        }
        return lm;
    }

    public boolean ok(int index) {
        return index >= 0 && index < COUNT && visible[index];
    }
}
