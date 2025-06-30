package cn.joker.webdav.database.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_key_value")
public class KeyValue {

    @TableId(type = IdType.ASSIGN_UUID)
    private String uuid;

    private String keyType;

    private String belong;

    private String key;

    private String value;
}
