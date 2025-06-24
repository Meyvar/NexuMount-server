package cn.joker.webdav.handle;

import cn.joker.webdav.business.entity.FileRessource;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.file.Path;
import java.util.List;

public interface FileHandle {

    /**
     * 文件列表
     * @param path 文件路径
     * @param uri 请求地址
     * @return 文件list
     */
    List<FileRessource> handlePropFind(Path path, String uri);

    /**
     * 获取资源
     * @param path 资源路径
     */
    void handleGet(Path path);

    /**
     * 上传资源
     * @param path 上传路径
     */
    void handlePut(Path path);

    /**
     * 删除资源
     * @param path 删除路径
     */
    void handleDelete(Path path);

    /**
     * 创建文件夹
     * @param path 创建路径
     */
    void handleMkcol(Path path);

    /**
     * 移动资源
     * @param sourcePath 源路径
     */
    void handleMove(Path sourcePath);
}
