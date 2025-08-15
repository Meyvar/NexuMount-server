package cn.joker.webdav.fileTask;

import cn.hutool.extra.spring.SpringUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.config.ExternalConfig;
import cn.joker.webdav.utils.SprintContextUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class FileTransferTask implements Runnable {

    protected String taskId;
    protected FileBucket fromBucket;
    protected FileBucket toBucket;
    protected String fromPath;
    protected String toPath;
    protected Path targetPath;
    protected String taskBufferSize;

    protected volatile boolean cancelled = false;

    protected final ReentrantLock pauseLock = new ReentrantLock();
    protected final Condition unpaused = pauseLock.newCondition();

    public abstract void taskContent(TaskManager tm, TaskMeta meta) throws Exception;

    TaskManager tm = SpringUtil.getBean(TaskManager.class);


    @Override
    public void run() {
        TaskMeta meta = tm.getTaskMeta(taskId);
        if (meta == null || tm.isCancelled(taskId)) {
            return;
        }

        try {

            ExternalConfig externalConfig = SprintContextUtil.getBean("externalConfig", ExternalConfig.class);

            // 确保父目录存在
            Path targetPath = Paths.get(externalConfig.getTargetPath());
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            this.targetPath = Paths.get(targetPath + "/" + taskId + Paths.get(fromPath).getFileName());

            taskContent(tm, meta);

            if (cancelled) {
                meta.setStatus(TaskStatus.CANCELLED);
            } else if (meta.getStatus() == TaskStatus.PAUSED) {
                meta.setStatus(TaskStatus.PAUSED);
            } else {
                meta.setStatus(TaskStatus.COMPLETED);
            }

        } catch (Exception e) {
            meta.setStatus(TaskStatus.ERROR);
            meta.setError(e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                Files.delete(targetPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public void resume() {
        pauseLock.lock();
        try {
            tm.getPausedTasks().put(taskId, false);
            unpaused.signal();
        } finally {
            pauseLock.unlock();
        }
    }
}
