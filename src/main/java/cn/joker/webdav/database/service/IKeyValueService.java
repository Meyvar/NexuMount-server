package cn.joker.webdav.database.service;

import cn.joker.webdav.database.entity.KeyValue;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IKeyValueService extends IService<KeyValue> {

    List<KeyValue> findListByKey(String keyType, String key, String value);

    <T> T findBusinessData(String keyType, String belong, Class<T> clazz);

    <T> List<T> findBusinessAll(String keyType, Class<T> clazz);

    <T> void saveBusinessData(String keyType, T t, Class<T> clazz);

    <T> void updateBusinessData(String keyType, T t, Class<T> clazz);

    <T> void deleteBusinessData(String keyType, String belong);
}
