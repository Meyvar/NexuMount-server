package cn.joker.webdav.fileTask;

import lombok.Data;

import java.time.Instant;

@Data
public class TaskMeta {
    private final String taskId;
    private volatile TaskStatus status;
    private volatile long transferredBytes;
    private volatile Instant lastUpdateTime;
    private volatile String describe;
    private volatile String schedule;
    private volatile String userToken;
    private volatile double progress;
    private volatile double elapsed;
    private volatile Exception error;

    public TaskMeta(String taskId, String userToken) {
        this.taskId = taskId;
        this.status = TaskStatus.PAUSED; // 默认暂停，启动时改为 RUNNING
        this.transferredBytes = 0;
        this.lastUpdateTime = Instant.now();
        this.schedule = "文件下载";
        this.userToken = userToken;
    }
}
