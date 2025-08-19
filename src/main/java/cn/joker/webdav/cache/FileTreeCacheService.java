package cn.joker.webdav.cache;


import cn.joker.webdav.webdav.entity.FileResource;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class FileTreeCacheService {

    private static final String ROOT_KEY = "ROOT";

    private final Cache<String, FileResource> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(1) // 只有一个根节点
            .build();

    @PostConstruct
    public void initRoot() {
        FileResource root = new FileResource();
        root.setType("folder");
        root.setName("/");
        root.setChildren(new ArrayList<>());
        cache.put(ROOT_KEY, root);
    }

    public void addChildren(String path, List<FileResource> children) {
        FileResource root = cache.getIfPresent(ROOT_KEY);
        if (root == null) throw new IllegalStateException("Root node not initialized");

        List<String> parts = splitPath(path);

        FileResource parent = root;
        for (String part : parts) {
            parent = findOrCreateChild(parent, part);
        }

        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }
        parent.getChildren().addAll(children);

        cache.put(ROOT_KEY, root);
    }

    public List<FileResource> getChildren(String path) {
        FileResource root = cache.getIfPresent(ROOT_KEY);
        if (root == null) return Collections.emptyList();

        List<String> parts = splitPath(path);

        FileResource node = root;
        for (String part : parts) {
            node = findChild(node, part);
            if (node == null) return Collections.emptyList();
        }

        return node.getChildren() != null ? node.getChildren() : Collections.emptyList();
    }

    private FileResource findOrCreateChild(FileResource parent, String name) {
        if (parent.getChildren() == null) {
            parent.setChildren(new ArrayList<>());
        }

        for (FileResource child : parent.getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }

        FileResource newChild = new FileResource();
        newChild.setName(name);
        newChild.setType("folder");
        newChild.setChildren(new ArrayList<>());

        parent.getChildren().add(newChild);
        return newChild;
    }

    public FileResource getNode(String path) {
        FileResource root = cache.getIfPresent(ROOT_KEY);
        if (root == null) {
            return null;
        }

        if ("/".equals(path)) {
            return root;
        }

        List<String> parts = splitPath(path);
        if (parts.isEmpty()) {
            return root;
        }

        FileResource current = root;
        for (String part : parts) {
            current = findChild(current, part);
            if (current == null) {
                return null; // 节点不存在
            }
        }
        return current;
    }

    private FileResource findChild(FileResource parent, String name) {
        if (parent.getChildren() == null) return null;
        for (FileResource child : parent.getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    private List<String> splitPath(String path) {
        if ("/".equals(path)) return Collections.emptyList();
        return Arrays.asList(path.replaceAll("^/|/$", "").split("/"));
    }

    public boolean removeChild(String path) {
        if ("/".equals(path)) {
            throw new IllegalArgumentException("Root node cannot be deleted");
        }

        FileResource root = cache.getIfPresent(ROOT_KEY);
        if (root == null) return false;

        List<String> parts = splitPath(path);
        if (parts.isEmpty()) return false;

        String targetName = parts.get(parts.size() - 1);
        List<String> parentPath = parts.subList(0, parts.size() - 1);

        FileResource parent = root;
        for (String part : parentPath) {
            parent = findChild(parent, part);
            if (parent == null) return false; // 父节点不存在
        }

        if (parent.getChildren() == null) return false;

        boolean removed = parent.getChildren().removeIf(child -> child.getName().equals(targetName));
        if (removed) {
            cache.put(ROOT_KEY, root);
        }
        return removed;
    }
}

