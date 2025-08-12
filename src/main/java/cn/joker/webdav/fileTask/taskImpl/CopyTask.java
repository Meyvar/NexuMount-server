package cn.joker.webdav.fileTask.taskImpl;

import cn.hutool.core.io.FileUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.*;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.SystemFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;

public class CopyTask extends FileTransferTask {

    public CopyTask(String taskId, FileBucket fromBucket, FileBucket toBucket, String fromPath, String toPath, String taskBufferSize) {
        super(taskId, fromBucket, toBucket, fromPath, toPath, taskBufferSize);
    }

    @Override
    public void taskContent(TaskManager tm, TaskMeta meta) throws Exception {
        meta.setFromPath(fromPath);

        String describe = fromPath + " copy to " + toPath;

        meta.setDescribe(describe + " (Downloading...)");

        IFileAdapter fromAdapter = SprintContextUtil.getBean(fromBucket.getAdapter(), IFileAdapter.class);
        IFileAdapter toAdapter = SprintContextUtil.getBean(toBucket.getAdapter(), IFileAdapter.class);

        String path = PathUtils.normalizePath("/" + fromPath.replaceFirst(fromBucket.getSourcePath(), ""));

        if (fromAdapter.getFolderItself(fromBucket, path).getType().equals("folder")) {
            toAdapter.mkcol(toBucket, toPath);

            List<FileResource> fileResourceList = fromAdapter.propFind(fromBucket, path, true);
            for (FileResource fileResource : fileResourceList) {

                String uuid = UUID.randomUUID().toString().replace("-", "");

                CopyTask copyTask = new CopyTask(uuid, fromBucket, toBucket, PathUtils.normalizePath(fromBucket.getSourcePath() + fileResource.getHref().replaceFirst(fromBucket.getPath(), "/")), toPath + "/" + fileResource.getName(), taskBufferSize);

                tm.startTask(uuid, copyTask, meta.getUserToken());
            }

            meta.setStatus(TaskStatus.COMPLETED);
            meta.setProgress("100");
            meta.setDescribe(describe + " (Success)");
        } else {
            if (fromAdapter instanceof SystemFileAdapter) {
                Files.copy(Paths.get(fromPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                String downloadUrl = fromAdapter.getDownloadUrl(fromBucket, Paths.get(fromPath).toString());

                Request request = new Request.Builder()
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

                while ((bytesRead = in.read(buffer)) != -1) {

                    // 检查暂停
                    synchronized (this) {
                        while (tm.isPaused(taskId)) {
                            this.wait(); // 线程挂起，等待唤醒
                        }
                    }

                    //任务取消
                    if (cancelled) {
                        return;
                    }

                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    meta.setTransferredBytes(downloaded);

                    // 计算进度
                    if (totalSize > 0) {
                        double progress = (downloaded * 100.0 / totalSize);
                        meta.setProgress(df.format(progress));
                    }

                    // 计算速度（KB/s）
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    if (elapsed > 0) {
                        double speed = downloaded / 1024.0 / elapsed;
                        meta.setElapsed(df.format(speed));
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
                @Override
                public void pause() throws InterruptedException {
                    synchronized (CopyTask.this) {
                        while (tm.isPaused(taskId)) {
                            CopyTask.this.wait(); // 线程挂起，等待唤醒
                        }
                    }
                }


                @Override
                public boolean cancel() {
                    return CopyTask.this.cancelled;
                }
            });

            // 更新进度
            meta.setStatus(TaskStatus.COMPLETED);

            meta.setDescribe(describe + " (Success)");
        }
    }
}
