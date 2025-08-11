package cn.joker.webdav.fileTask.taskImpl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.FileTransferTask;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.fileTask.TaskStatus;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.SystemFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CopyTask extends FileTransferTask {

    public CopyTask(String taskId, FileBucket fromBucket, FileBucket toBucket, String fromPath, String toPath, String taskBufferSize) {
        super(taskId, fromBucket, toBucket, fromPath, toPath, taskBufferSize);
    }

    @Override
    public void taskContent(TaskManager tm, TaskMeta meta) throws Exception {
        String describe = fromPath + " copy to " + toPath;

        meta.setDescribe(describe + " (Downloading...)");

        IFileAdapter fromAdapter = SprintContextUtil.getBean(fromBucket.getAdapter(), IFileAdapter.class);
        IFileAdapter toAdapter = SprintContextUtil.getBean(toBucket.getAdapter(), IFileAdapter.class);

        if (fromAdapter instanceof SystemFileAdapter) {
            Files.copy(Paths.get(fromPath), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            String downloadUrl = fromAdapter.getDownloadUrl(fromBucket, Paths.get(fromPath).toString());

            Request request = new Request.Builder()
                    .url(downloadUrl)
                    .build();

            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()){
                throw  new IOException("Unexpected code " + response);
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

            while ((bytesRead = in.read(buffer)) != -1) {

                // 检查暂停
                if (tm.isPaused(taskId)) {
                    meta.setStatus(TaskStatus.PAUSED);
                    return; // 退出，线程释放
                }

                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                meta.setTransferredBytes(downloaded);

                // 计算进度
                if (totalSize > 0) {
                    double progress = (downloaded * 100.0 / totalSize);
                    meta.setProgress(progress);
                }

                // 计算速度（KB/s）
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > 0) {
                    double speed = downloaded / 1024.0 / elapsed;
                    meta.setElapsed(speed);
                }
            }

            out.flush();
            in.close();

        }

        meta.setDescribe(describe + " (Uploading...)");

        toAdapter.put(toBucket, toPath, targetPath);

        // 更新进度
        meta.setTransferredBytes(0);
        meta.setStatus(TaskStatus.RUNNING);

        meta.setDescribe(describe + " (Success)");


    }
}
