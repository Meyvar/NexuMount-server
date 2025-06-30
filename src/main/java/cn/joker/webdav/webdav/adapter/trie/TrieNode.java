package cn.joker.webdav.webdav.adapter.trie;

import cn.joker.webdav.business.entity.FileBucket;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    Map<String, TrieNode> children = new HashMap<>();
    FileBucket bucket = null;
}
