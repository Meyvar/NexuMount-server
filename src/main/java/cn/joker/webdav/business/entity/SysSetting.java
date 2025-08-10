package cn.joker.webdav.business.entity;

import lombok.Data;

@Data
public class SysSetting {

    String uuid;

    String webTitle;

    String webScript;

    String webStyle;

    String previewServer;

    String previewText;

    String previewAudio;

    String previewVideo;

    String previewImage;

    String taskUploadNumber;

    String taskCopyNumber;

}
