package cn.joker.webdav.fileTask;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.WebDavServerApplication;
import cn.joker.webdav.business.entity.FileBucket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class FileTransferTask implements Runnable {

    protected String taskId;
    protected FileBucket fromBucket;
    protected FileBucket toBucket;
    protected String fromPath;
    protected String toPath;
    protected Path targetPath;
    protected String taskBufferSize;

    protected volatile boolean cancelled = false;

    public FileTransferTask(String taskId, FileBucket fromBucket, FileBucket toBucket, String fromPath, String toPath, String taskBufferSize) {
        this.taskId = taskId;
        this.fromBucket = fromBucket;
        this.toBucket = toBucket;
        this.fromPath = fromPath;
        this.toPath = toPath;
        this.taskBufferSize = taskBufferSize;
    }

    public abstract void taskContent(TaskManager tm, TaskMeta meta) throws Exception;

    @Override
    public void run() {
        TaskManager tm = TaskManager.getInstance();
        TaskMeta meta = tm.getTaskMeta(taskId);
        if (meta == null) {
            return;
        }

        try {
            String jarPath = Paths.get(
                    WebDavServerApplication.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).toFile().getParent();

            // 确保父目录存在
            Path targetPath = Paths.get(jarPath + "/temp/");
            if (!Files.exists(targetPath)) {
                Files.createDirectories(targetPath);
            }

            this.targetPath = Paths.get(targetPath + "/" + Paths.get(fromPath).getFileName());

            taskContent(tm, meta);

            if (cancelled) {
                meta.setStatus(TaskStatus.CANCELLED);
                System.out.println("任务 " + taskId + " 取消");
            } else {
                meta.setStatus(TaskStatus.COMPLETED);
                System.out.println("任务 " + taskId + " 完成");
            }

        } catch (Exception e) {
            meta.setStatus(TaskStatus.ERROR);
            meta.setError(e);
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
}
