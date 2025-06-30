package cn.joker.webdav.webdav.adapter.trie;

import cn.joker.webdav.business.entity.FileBucket;

import java.util.ArrayList;
import java.util.List;

public class FileBucketPathUtils {
    public static PathMatchResult matchSelfAndDirectChildren(String basePath, List<FileBucket> buckets) {
        basePath = normalize(basePath);
        FileBucket self = null;
        List<FileBucket> children = new ArrayList<>();

        for (FileBucket bucket : buckets) {
            String path = normalize(bucket.getPath());

            if (path.equals(basePath)) {
                self = bucket;
            } else if (path.startsWith(basePath + "/")) {
                String sub = path.substring(basePath.length() + 1);
                if (!sub.contains("/")) {
                    children.add(bucket);
                }
            }
        }

        return new PathMatchResult(self, children);
    }

    /**
     * 找出最长的“父路径” FileBucket
     * 即：路径 path 是 target 的前缀，并且是最长的那个
     */
    public static FileBucket findLongestPrefix(String target, List<FileBucket> buckets) {
        target = normalize(target);
        FileBucket best = null;

        for (FileBucket bucket : buckets) {
            String candidate = normalize(bucket.getPath());
            if (target.equals(candidate) || target.startsWith(candidate + "/")) {
                if (best == null || candidate.length() > normalize(best.getPath()).length()) {
                    best = bucket;
                }
            }
        }

        return best;
    }

    public static List<FileBucket> findDirectChildren(String basePath, List<FileBucket> buckets) {
        basePath = normalize(basePath);
        List<FileBucket> result = new ArrayList<>();

        for (FileBucket path : buckets) {
            String pathB = normalize(path.getPath());
            if (pathB.startsWith(basePath + "/")) {
                String sub = pathB.substring(basePath.length() + 1);
                if (!sub.contains("/")) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    private static String normalize(String path) {
        return path.replaceAll("/{2,}", "/").replaceAll("/$", "");
    }
}
