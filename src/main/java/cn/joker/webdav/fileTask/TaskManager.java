package cn.joker.webdav.fileTask;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class TaskManager {

    @Getter
    private ExecutorService executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

    @Getter
    private Semaphore semaphore;

    private final AtomicInteger maxPermits =  new AtomicInteger(0);

    @Getter
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);

    private final Map<String, TaskMeta> taskMetaMap = new ConcurrentHashMap<>();
    private final Map<String, FileTransferTask> runningTasks = new ConcurrentHashMap<>();

    @Getter
    private final ConcurrentMap<String, Boolean> pausedTasks = new ConcurrentHashMap<>();

    private TaskManager() {
        semaphore =  new Semaphore(maxPermits.get());
    }

    public TaskMeta getTaskMeta(String taskId) {
        return taskMetaMap.get(taskId);
    }

    public void setMaxPermits(int newMax) {
        int oldMax = maxPermits.getAndSet(newMax);
        int diff = newMax - oldMax;
        if (diff > 0) {
            semaphore.release(diff);  // 增加并发额度
        } else if (diff < 0) {
            try {
                semaphore.acquire(-diff); // 占用多余的 permits
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void startTask(String taskId, FileTransferTask task, String userToken) {
        TaskMeta meta = taskMetaMap.computeIfAbsent(taskId, id -> new TaskMeta(taskId, userToken));
        pausedTasks.put(taskId, false);
        meta.setStatus(TaskStatus.RUNNING);

        meta.setDescribe(task.fromPath);
        runningTasks.put(taskId, task);
        executor.submit(() -> {
            try {
                semaphore.acquire(); // 获取许可，控制并发
                try {
                    task.run();
                } finally {
                    semaphore.release(); // 释放许可
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
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
        FileTransferTask task = runningTasks.get(taskId);
        task.pauseLock.lock();
        try {
            task.unpaused.signal(); // 唤醒挂起的虚拟线程
        } finally {
            task.pauseLock.unlock();
        }
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

    public boolean isCancelled(String taskId) {
        TaskMeta meta = taskMetaMap.get(taskId);
        return meta != null && meta.getStatus() == TaskStatus.CANCELLED;
    }

    public TaskMeta getTaskStatus(String taskId) {
        return taskMetaMap.get(taskId);
    }

    public List<TaskMeta> getRunningTasks() {
        return taskMetaMap.values().stream()
                .filter(meta -> meta.getStatus() == TaskStatus.RUNNING || meta.getStatus() == TaskStatus.PAUSED)
                .collect(Collectors.toList());
    }

    public List<TaskMeta> getCompletedTasks() {
        return taskMetaMap.values().stream()
                .filter(meta -> meta.getStatus() == TaskStatus.COMPLETED || meta.getStatus() == TaskStatus.CANCELLED || meta.getStatus() == TaskStatus.ERROR)
                .collect(Collectors.toList());
    }

    public void removeAllCompletedTasks() {
        List<String> toRemove = taskMetaMap.entrySet().stream()
                .filter(entry -> {
                    TaskStatus status = entry.getValue().getStatus();
                    return status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        toRemove.forEach(taskId -> {
            taskMetaMap.remove(taskId);
            runningTasks.remove(taskId);
            pausedTasks.remove(taskId);
        });
    }

    public void restartFailedTask(String taskId) {
        TaskMeta meta = taskMetaMap.get(taskId);
        if (meta == null) {
            System.out.println("任务不存在: " + taskId);
            return;
        }
        if (meta.getStatus() != TaskStatus.ERROR) {
            System.out.println("任务状态不是失败，无法重启: " + taskId);
            return;
        }
        startTask(taskId, runningTasks.get(taskId), meta.getUserToken());
    }

    public void removeTask(String taskId) {
        // 先取消任务（如果正在执行）
        FileTransferTask task = runningTasks.remove(taskId);
        if (task != null) {
            task.cancel();
        }
        // 移除任务元信息和暂停状态
        taskMetaMap.remove(taskId);
        pausedTasks.remove(taskId);
    }
}

