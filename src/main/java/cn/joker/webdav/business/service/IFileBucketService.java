package cn.joker.webdav.business.service;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.entity.SysUser;

import java.util.List;
import java.util.Map;

public interface IFileBucketService {

    List<FileBucket> findAll();

    List<Map<String, String>> getFileAdapterList();

    void save(FileBucket fileBucket);

}
