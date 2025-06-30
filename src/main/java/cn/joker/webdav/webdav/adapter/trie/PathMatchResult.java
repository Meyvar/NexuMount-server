package cn.joker.webdav.webdav.adapter.trie;

import cn.joker.webdav.business.entity.FileBucket;

import java.util.List;

public class PathMatchResult {

    private FileBucket self;
    private List<FileBucket> directChildren;

    public PathMatchResult(FileBucket self, List<FileBucket> directChildren) {
        this.self = self;
        this.directChildren = directChildren;
    }

    public FileBucket getSelf() {
        return self;
    }

    public List<FileBucket> getDirectChildren() {
        return directChildren;
    }

}
