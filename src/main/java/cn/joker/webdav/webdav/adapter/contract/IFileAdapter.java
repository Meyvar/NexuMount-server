package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface IFileAdapter {


    /**
     * 路径是否存在
     *
     * @param path 资源路径
     * @return
     */
    boolean hasPath(FileBucket fileBucket, String path);


    /**
     * 获取文件夹本身
     *
     * @return
     */
    FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException;

    /**
     * 文件列表
     *
     * @param fileBucket 文件桶
     * @param uri        请求地址
     * @return 文件list
     */
    List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException;

    /**
     * 获取资源
     *
     * @param fileBucket 文件桶
     * @param path       资源路径
     */
    void get(FileBucket fileBucket, String path) throws Exception;

    /**
     * 上传资源
     *
     * @param fileBucket 文件桶
     * @param path       上传路径
     */
    void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception;

    /**
     * 删除资源
     *
     * @param fileBucket 文件桶
     * @param path       删除路径
     */
    void delete(FileBucket fileBucket, String path) throws IOException;

    /**
     * 创建文件夹
     *
     * @param fileBucket 文件桶
     * @param path       创建路径
     */
    void mkcol(FileBucket fileBucket, String path) throws IOException;

    /**
     * 移动资源
     *
     * @param fromFileBucket 文件桶
     * @param fromPath 源路径
     */
    void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException;

    /**
     * 文件复制
     *
     * @param fromFileBucket 文件桶
     * @param fromPath
     * @throws IOException
     */
    void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException;


    /**
     * 获取下载地址
     *
     * @param path
     * @return
     */
    String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException;


    /**
     * 存储桶工作状态
     *
     * @param fileBucket
     * @return
     */
    String workStatus(FileBucket fileBucket);


    /**
     * 刷新token
     * @param fileBucket
     */
    FileBucket refreshToken(FileBucket fileBucket);
}
