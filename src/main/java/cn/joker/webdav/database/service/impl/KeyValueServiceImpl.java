package cn.joker.webdav.database.service.impl;

import cn.joker.webdav.database.entity.KeyValue;
import cn.joker.webdav.database.mapper.KeyValueMapper;
import cn.joker.webdav.database.service.IKeyValueService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class KeyValueServiceImpl extends ServiceImpl<KeyValueMapper, KeyValue> implements IKeyValueService {
    @Override
    public List<KeyValue> findListByKey(String keyType, String key, String value) {
        QueryWrapper<KeyValue> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(KeyValue::getKeyType, keyType)
                .eq(KeyValue::getKey, key)
                .eq(KeyValue::getValue, value);
        return list(queryWrapper);
    }

    @Override
    public <T> T findBusinessData(String keyType, String belong, Class<T> clazz) {
        QueryWrapper<KeyValue> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(KeyValue::getKeyType, keyType)
                .eq(KeyValue::getBelong, belong);
        List<KeyValue> list = list(queryWrapper);

        JSONObject jsonObject = new JSONObject();
        for (KeyValue keyValue : list) {
            jsonObject.put(keyValue.getKey(), keyValue.getValue());
        }
        return jsonObject.toJavaObject(clazz);
    }

    @Override
    public <T> List<T> findBusinessAll(String keyType, Class<T> clazz) {
        QueryWrapper<KeyValue> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(KeyValue::getKeyType, keyType);
        List<KeyValue> list = list(queryWrapper);

        Map<String, List<KeyValue>> map = new HashMap<>();

        for (KeyValue keyValue : list) {
            if (!map.containsKey(keyValue.getBelong())) {
                map.put(keyValue.getBelong(), new ArrayList<>());
            }
            map.get(keyValue.getBelong()).add(keyValue);
        }

        List<T> tList = new ArrayList<>();

        for (Map.Entry<String, List<KeyValue>> entry : map.entrySet()) {
            JSONObject jsonObject = new JSONObject();
            for (KeyValue keyValue : entry.getValue()) {
                jsonObject.put(keyValue.getKey(), keyValue.getValue());
            }
            tList.add(jsonObject.toJavaObject(clazz));
        }

        return tList;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public <T> void saveBusinessData(String keyType, T t, Class<T> clazz) {
        JSONObject jsonObject = JSON.parseObject(JSONObject.toJSONString(t));

        KeyValue keyValue = new KeyValue();
        keyValue.setKeyType(keyType);
        keyValue.setKey("uuid");
        keyValue.setValue(UUID.randomUUID().toString().replace("-", ""));
        keyValue.setBelong(keyValue.getValue());
        save(keyValue);


        for (String s : jsonObject.keySet()) {
            KeyValue keyValueItem = new KeyValue();
            keyValueItem.setKeyType(keyType);
            keyValueItem.setBelong(keyValue.getValue());
            keyValueItem.setKey(s);
            keyValueItem.setValue(jsonObject.getString(s));
            save(keyValueItem);
        }

        t = findBusinessData(keyType, keyValue.getValue(), clazz);
    }

    @Override
    public <T> void updateBusinessData(String keyType, T t, Class<T> clazz) {
        JSONObject jsonObject = JSON.parseObject(JSONObject.toJSONString(t));

        for (String s : jsonObject.keySet()) {
            if (!StringUtils.hasText(s) || "uuid".equals(s) || jsonObject.getString(s) == null) {
                continue;
            }
            UpdateWrapper<KeyValue> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().eq(KeyValue::getKeyType, keyType)
                    .eq(KeyValue::getBelong, jsonObject.getString("uuid"))
                    .eq(KeyValue::getKey, s)
                    .set(KeyValue::getValue, jsonObject.getString(s));
            if (!update(updateWrapper)) {
                KeyValue keyValueItem = new KeyValue();
                keyValueItem.setKeyType(keyType);
                keyValueItem.setBelong(jsonObject.getString("uuid"));
                keyValueItem.setKey(s);
                keyValueItem.setValue(jsonObject.getString(s));
                save(keyValueItem);
            }
        }
    }

    @Override
    public <T> void deleteBusinessData(String keyType, String belong) {
        QueryWrapper<KeyValue> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(KeyValue::getKeyType, keyType)
                .eq(KeyValue::getBelong, belong);
        remove(queryWrapper);
    }


}
