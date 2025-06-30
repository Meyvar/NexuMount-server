package cn.joker.webdav.webdav.adapter.trie;

import cn.joker.webdav.business.entity.FileBucket;

public class PathTrie {

    private final TrieNode root = new TrieNode();

    public void insert(FileBucket bucket) {
        String[] parts = cleanPath(bucket.getPath()).split("/");
        TrieNode node = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            node = node.children.computeIfAbsent(part, k -> new TrieNode());
        }
        node.bucket = bucket;
    }

    public FileBucket longestMatch(String path) {
        String[] parts = cleanPath(path).split("/");
        TrieNode node = root;
        FileBucket result = null;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            node = node.children.get(part);
            if (node == null) break;
            if (node.bucket != null) {
                result = node.bucket;
            }
        }

        return result;
    }

    private String cleanPath(String path) {
        return path.replaceAll("/{2,}", "/").replaceAll("/$", "");
    }

}
