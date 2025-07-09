package cn.joker.webdav.webdav.adapter.trie;

import cn.joker.webdav.business.entity.FileBucket;

import java.util.ArrayList;
import java.util.List;

public class FileBucketPathUtils {

    // 匹配当前路径自身和所有直接子目录（一级）
    public static PathMatchResult matchSelfAndDirectChildren(String basePath, List<FileBucket> buckets) {
        basePath = normalize(basePath);

        FileBucket self = null;
        List<FileBucket> children = new ArrayList<>();

        for (FileBucket bucket : buckets) {
            String path = normalize(bucket.getPath());

            if (path.equals(basePath)) {
                self = bucket;
            } else {
                if ("/".equals(basePath)) {
                    // 根路径，只匹配一级：/abc 但不包括 /abc/def
                    if (!path.substring(1).contains("/")) {
                        children.add(bucket);
                    }
                } else if (path.startsWith(basePath + "/")) {
                    String sub = path.substring(basePath.length() + 1);
                    if (!sub.contains("/")) {
                        children.add(bucket);
                    }
                }
            }
        }

        return new PathMatchResult(self, children);
    }

    // 匹配当前路径自身和所有子孙路径（不限层级）
    public static PathMatchResult matchSelfAndAllDescendants(String basePath, List<FileBucket> buckets) {
        basePath = normalize(basePath);

        FileBucket self = null;
        List<FileBucket> descendants = new ArrayList<>();

        for (FileBucket bucket : buckets) {
            String path = normalize(bucket.getPath());

            if (path.equals(basePath)) {
                self = bucket;
            } else if ("/".equals(basePath)) {
                // 根路径匹配所有非根路径
                if (!path.equals("/")) {
                    descendants.add(bucket);
                }
            } else if (path.startsWith(basePath + "/")) {
                descendants.add(bucket);
            }
        }

        return new PathMatchResult(self, descendants);
    }

    // 只找一级子目录
    public static List<FileBucket> findDirectChildren(String basePath, List<FileBucket> buckets) {
        basePath = normalize(basePath);
        List<FileBucket> result = new ArrayList<>();

        for (FileBucket bucket : buckets) {
            String path = normalize(bucket.getPath());

            if ((basePath.equals("/") && path.indexOf('/', 1) == -1) ||
                    (path.startsWith(basePath + "/") && !path.substring(basePath.length() + 1).contains("/"))) {
                result.add(bucket);
            }
        }

        return result;
    }

    // 找出最长的“父路径” FileBucket
    public static FileBucket findLongestPrefix(String target, List<FileBucket> buckets) {
        target = normalize(target);
        FileBucket best = null;

        for (FileBucket bucket : buckets) {
            String candidate = normalize(bucket.getPath());

            boolean matches =
                    target.equals(candidate) ||
                            ("/".equals(candidate)) ||
                            target.startsWith(candidate + "/");

            if (matches) {
                if (best == null || candidate.length() > normalize(best.getPath()).length()) {
                    best = bucket;
                }
            }
        }

        return best;
    }

    // 统一路径格式处理
    private static String normalize(String path) {
        if (path == null || path.isEmpty()) return "";
        path = path.replaceAll("/{2,}", "/"); // 多个 / 合并
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}