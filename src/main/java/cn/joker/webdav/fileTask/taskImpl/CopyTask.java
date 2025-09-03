package cn.joker.webdav.fileTask.taskImpl;

import cn.hutool.core.io.FileUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.*;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.SystemFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import kotlin.time.TimeMark;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CopyTask extends FileTransferTask {

    public CopyTask(String taskId, FileBucket fromBucket, FileBucket toBucket, String fromPath, String toPath, String taskBufferSize) {
        this.taskId = taskId;
        this.fromBucket = fromBucket;
        this.toBucket = toBucket;
        this.fromPath = fromPath;
        this.toPath = toPath;
        this.taskBufferSize = taskBufferSize;
    }

    @Override
    public void taskContent(TaskManager tm, TaskMeta meta) throws Exception {
        meta.setFromPath(fromPath);

        String describe = fromPath + " copy to " + toPath;

        meta.setSchedule("文件下载");
        meta.setDescribe(describe + " (Downloading...)");

        IFileAdapter fromAdapter = SprintContextUtil.getBean(fromBucket.getAdapter(), IFileAdapter.class);
        IFileAdapter toAdapter = SprintContextUtil.getBean(toBucket.getAdapter(), IFileAdapter.class);

        if (fromAdapter.getFolderItself(fromBucket, fromPath).getType().equals("folder")) {
            toAdapter.mkcol(toBucket, toPath);

            List<FileResource> fileResourceList = fromAdapter.propFind(fromBucket, fromPath, true);
            for (FileResource fileResource : fileResourceList) {

                String uuid = UUID.randomUUID().toString().replace("-", "");

                CopyTask copyTask = new CopyTask(uuid, fromBucket, toBucket, fromPath + "/" + fileResource.getName(), toPath + "/" + fileResource.getName(), taskBufferSize);

                tm.startTask(uuid, copyTask, meta.getUserToken());
            }

            meta.setStatus(TaskStatus.COMPLETED);
            meta.setProgress("100");
            meta.setDescribe(describe + " (Success)");
        } else {
            if (fromAdapter instanceof SystemFileAdapter) {
                Files.copy(Paths.get(fromPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Map<String, String> headerMap = new HashMap<>();

                String downloadUrl = fromAdapter.getDownloadUrl(fromBucket, PathUtils.toLinuxPath(Paths.get(fromPath)), headerMap);

                Headers headers = Headers.of(headerMap);

                Request request = new Request.Builder()
                        .headers(headers)
                        .url(downloadUrl)
                        .build();


                OkHttpClient client = new OkHttpClient();
                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                ResponseBody body = response.body();

                // 获取文件总大小
                long totalSize = body.contentLength();

                InputStream in = body.byteStream();
                OutputStream out = FileUtil.getOutputStream(targetPath);

                byte[] buffer = new byte[1024 * 1024 * Integer.parseInt(taskBufferSize)];
                int bytesRead;
                long downloaded = 0;

                long startTime = System.currentTimeMillis();


                DecimalFormat df = new DecimalFormat("#.##");

                long lastDownloaded = 0;

                boolean released = false;
                while ((bytesRead = in.read(buffer)) != -1) {

                    if (tm.isPaused(taskId)) {
                        if (!released) {
                            tm.getSemaphore().release(); // 只释放一次
                            released = true;
                        }

                        pauseLock.lock();
                        try {
                            while (tm.isPaused(taskId)) {
                                unpaused.await(); // 挂起虚拟线程
                            }
                        } finally {
                            pauseLock.unlock();
                        }

                        if (released) {
                            tm.getSemaphore().acquire(); // 恢复时重新占用名额
                            released = false; // 重置
                        }
                    }


                    //任务取消
                    if (cancelled) {
                        return;
                    }

                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    // 计算进度
                    if (totalSize > 0) {
                        double progress = (downloaded * 100.0 / totalSize);
                        meta.setProgress(df.format(progress));
                    }

                    // 计算速度（KB/s）
                    long endTime = System.currentTimeMillis();
                    double elapsed = (endTime - startTime) / 1000.0;
                    if (elapsed > 1) {
                        long downloadedSize = downloaded - lastDownloaded;
                        double speed = downloadedSize / 1024.0 / elapsed;
                        meta.setElapsed(df.format(speed));
                        startTime = endTime;
                        lastDownloaded = downloaded;
                    }
                }

                out.flush();
                in.close();

            }

            meta.setDescribe(describe + " (Uploading...)");

            meta.setElapsed("0");
            meta.setProgress("0");
            meta.setSchedule("文件上传");

            toAdapter.put(toBucket, toPath, targetPath, new UploadHook() {
                private boolean released = false;

                @Override
                public void pause() throws InterruptedException {
                    if (tm.isPaused(taskId)) {
                        if (!released) {
                            tm.getSemaphore().release(); // 只释放一次
                            released = true;
                        }

                        pauseLock.lock();
                        try {
                            while (tm.isPaused(taskId)) {
                                unpaused.await(); // 挂起虚拟线程（不会占用 OS 线程）
                            }
                        } finally {
                            pauseLock.unlock();
                        }

                        if (released) {
                            tm.getSemaphore().acquire(); // 恢复时重新占用名额
                            released = false; // 重置状态
                        }
                    }
                }


                @Override
                public boolean cancel() {
                    return CopyTask.this.cancelled;
                }

                @Override
                public TaskMeta getTaskMeta() {
                    return meta;
                }
            });

            // 更新进度
            meta.setStatus(TaskStatus.COMPLETED);

            meta.setDescribe(describe + " (Success)");
        }
    }
}
