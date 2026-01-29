package com.jd.oxygent.core.oxygent.utils;

public class OSUtil {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }

    public static boolean isMac() {
        return OS_NAME.contains("mac");
    }

    public static boolean isLinux() {
        return OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix");
    }

    // 可选：提供一个枚举方式统一判断
    public enum OSType {
        WINDOWS, MAC, LINUX, UNKNOWN
    }

    public static OSType getOSType() {
        if (isWindows()) {
            return OSType.WINDOWS;
        } else if (isMac()) {
            return OSType.MAC;
        } else if (isLinux()) {
            return OSType.LINUX;
        } else {
            return OSType.UNKNOWN;
        }
    }

    // 示例用法
    public static void main(String[] args) {
        System.out.println("Operating System: " + getOSType());
    }
}