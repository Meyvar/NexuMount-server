package cn.joker.webdav.fileTask;

import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import kotlin.time.TimeMark;

public interface UploadHook {

    void pause() throws InterruptedException;

    boolean cancel();

    TaskMeta getTaskMeta();
}
