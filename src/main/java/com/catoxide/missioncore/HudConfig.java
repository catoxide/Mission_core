package com.catoxide.missioncore;

public class HudConfig {
    public static boolean enabled = true;
    public static int xOffset = 5;
    public static int yOffset = 5;
    public static int opacity = 100;

    // 计算带透明度的颜色
    public static int getColorWithOpacity(int baseColor) {
        if (opacity == 100) return baseColor;
        int alpha = (int)((opacity / 100.0) * 255);
        return (alpha << 24) | (baseColor & 0xFFFFFF);
    }
}
