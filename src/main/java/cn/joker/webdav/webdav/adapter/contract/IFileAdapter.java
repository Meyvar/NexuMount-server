package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.webdav.entity.FileRessource;

import java.nio.file.Path;
import java.util.List;

public interface IFileAdapter {


    /**
     * 路径是否存在
     *
     * @param path 资源路径
     * @return
     */
    boolean hasPath(Path path);


    /**
     * 获取文件夹本身
     *
     * @return
     */
    FileRessource getFolderItself(String path);

    /**
     * 文件列表
     *
     * @param path 文件路径
     * @param uri  请求地址
     * @return 文件list
     */
    List<FileRessource> propFind(String path, String uri);

    /**
     * 获取资源
     *
     * @param path 资源路径
     */
    void get(Path path);

    /**
     * 上传资源
     *
     * @param path 上传路径
     */
    void put(Path path);

    /**
     * 删除资源
     *
     * @param path 删除路径
     */
    void delete(Path path);

    /**
     * 创建文件夹
     *
     * @param path 创建路径
     */
    void mkcol(Path path);

    /**
     * 移动资源
     *
     * @param sourcePath 源路径
     */
    void move(Path sourcePath);

}
