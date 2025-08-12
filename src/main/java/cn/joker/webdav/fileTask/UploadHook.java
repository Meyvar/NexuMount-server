package cn.joker.webdav.fileTask;

import cn.joker.webdav.fileTask.taskImpl.CopyTask;

public interface UploadHook {

    void pause() throws InterruptedException;


    boolean cancel();
}
