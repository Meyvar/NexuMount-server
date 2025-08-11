package cn.joker.webdav.fileTask;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class TaskManager {

    private static TaskManager INSTANCE = new TaskManager();

    @Qualifier("fileTransferExecutor")
    @Autowired
    private ThreadPoolTaskExecutor executor;

    private final Map<String, TaskMeta> taskMetaMap = new ConcurrentHashMap<>();
    private final Map<String, FileTransferTask> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> pausedTasks = new ConcurrentHashMap<>();

    private TaskManager() {
    }

    @PostConstruct
    public void init() {
        INSTANCE = this; // 保存 Spring 管理的实例
    }

    public static TaskManager getInstance() {
        return INSTANCE;
    }

    public TaskMeta getTaskMeta(String taskId) {
        return taskMetaMap.get(taskId);
    }

    public void startTask(String taskId, FileTransferTask task, String userToken) {
        TaskMeta meta = taskMetaMap.computeIfAbsent(taskId, id -> new TaskMeta(taskId, userToken));
        pausedTasks.put(taskId, false);
        meta.setStatus(TaskStatus.RUNNING);

        runningTasks.put(taskId, task);
        executor.submit(task);
    }

    public void pauseTask(String taskId) {
        pausedTasks.put(taskId, true);
        TaskMeta meta = taskMetaMap.get(taskId);
        if (meta != null) {
            meta.setStatus(TaskStatus.PAUSED);
        }
    }

    public void resumeTask(String taskId) {
        Boolean paused = pausedTasks.get(taskId);
        if (paused == null || !paused) return; // 未暂停直接返回

        pausedTasks.put(taskId, false);
        TaskMeta meta = taskMetaMap.get(taskId);
        if (meta == null) return;

        meta.setStatus(TaskStatus.RUNNING);

        // 从进度点继续传输
        FileTransferTask task = null;
        runningTasks.put(taskId, task);
        executor.submit(task);
    }

    public void cancelTask(String taskId) {
        pausedTasks.remove(taskId);
        FileTransferTask task = runningTasks.remove(taskId);
        if (task != null) {
            task.cancel();
        }
        TaskMeta meta = taskMetaMap.get(taskId);
        if (meta != null) {
            meta.setStatus(TaskStatus.CANCELLED);
        }
    }

    public boolean isPaused(String taskId) {
        return pausedTasks.getOrDefault(taskId, false);
    }

    public TaskMeta getTaskStatus(String taskId) {
        return taskMetaMap.get(taskId);
    }
}

