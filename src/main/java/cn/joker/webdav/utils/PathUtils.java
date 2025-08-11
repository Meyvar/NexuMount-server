package cn.joker.webdav.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtils {

    /**
     * 获取Path对象对应的字符串，使用系统默认分隔符
     */
    public static String toSystemPath(Path path) {
        if (path == null) return null;
        return path.toString();
    }

    /**
     * 强制把Path转换成Linux格式字符串（全部用 '/'）
     */
    public static String toLinuxPath(Path path) {
        if (path == null) return null;
        return path.toString().replace("\\", "/");
    }

    /**
     * 强制把Path转换成Windows格式字符串（全部用 '\'）
     */
    public static String toWindowsPath(Path path) {
        if (path == null) return null;
        return path.toString().replace("/", "\\");
    }

    /**
     * 跨平台拼接多个路径字符串，返回Path对象
     */
    public static Path join(String... parts) {
        return Paths.get("", parts);
    }

    /**
     * 跨平台拼接多个路径字符串，返回系统格式字符串
     */
    public static String joinToString(String... parts) {
        return join(parts).toString();
    }


    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        // 统一为 / 分隔
        String unixPath = path.replace("\\", "/");

        // 去掉重复的 /
        unixPath = unixPath.replaceAll("/+", "/");

        // 去掉末尾的 /（根路径 "/" 除外）
        if (unixPath.length() > 1 && unixPath.endsWith("/")) {
            unixPath = unixPath.substring(0, unixPath.length() - 1);
        }

        return unixPath;
    }
}
