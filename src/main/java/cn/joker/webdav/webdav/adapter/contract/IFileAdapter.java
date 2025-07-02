package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface IFileAdapter {


    /**
     * 路径是否存在
     *
     * @param path 资源路径
     * @return
     */
    boolean hasPath(String path);


    /**
     * 获取文件夹本身
     *
     * @return
     */
    FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException;

    /**
     * 文件列表
     *
     * @param path 文件路径
     * @param uri  请求地址
     * @return 文件list
     */
    List<FileResource> propFind(FileBucket fileBucket, String uri) throws IOException;

    /**
     * 获取资源
     *
     * @param path 资源路径
     */
    GetFileResource get(String path) throws IOException;

    /**
     * 上传资源
     *
     * @param path 上传路径
     */
    void put(String path) throws IOException;

    /**
     * 删除资源
     *
     * @param path 删除路径
     */
    void delete(String path) throws IOException;

    /**
     * 创建文件夹
     *
     * @param path 创建路径
     */
    void mkcol(String path) throws IOException;

    /**
     * 移动资源
     *
     * @param sourcePath 源路径
     */
    void move(String sourcePath, String destPath)  throws IOException;

    /**
     * 文件发展
     * @param sourcePath
     * @param destPath
     * @throws IOException
     */
    void copy(String sourcePath, String destPath) throws IOException;
}
