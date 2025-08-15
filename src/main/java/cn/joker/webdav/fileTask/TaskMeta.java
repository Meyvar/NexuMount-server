package cn.joker.webdav.fileTask;

import lombok.Data;

import java.time.Instant;

@Data
public class TaskMeta {

    //任务id
    private final String taskId;

    //任务状态
    private volatile TaskStatus status;

    //任务大小
    private volatile long transferredBytes;

    //最后更新时间
    private volatile Instant lastUpdateTime;

    //任务说明
    private volatile String describe;

    //任务环节
    private volatile String schedule;

    //用户token
    private volatile String userToken;

    //任务进度
    private volatile String progress;

    //任务传输速度
    private volatile String elapsed;

    //错误信息
    private volatile String error;

    //文件源
    private String fromPath;

    public TaskMeta(String taskId, String userToken) {
        this.taskId = taskId;
        this.status = TaskStatus.PAUSED; // 默认暂停，启动时改为 RUNNING
        this.transferredBytes = 0;
        this.lastUpdateTime = Instant.now();
        this.schedule = "等待任务执行";
        this.setProgress("0");
        this.userToken = userToken;
    }
}
