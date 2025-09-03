package cn.joker.webdav.utils.fileUpload;

import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.fileTask.UploadHook;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;

public class UploadInputStream extends FileInputStream {

    private UploadHook hook;
    private TaskMeta meta;
    private long uploadedBytes = 0;
    DecimalFormat df = new DecimalFormat("#.##");
    private long totalBytes;
    private long historySize;


    @SneakyThrows
    @Override
    public int read(@NotNull byte[] b) throws IOException {
        if (hook != null) {
            hook.pause();
            if (hook.cancel()) {
                throw new RuntimeException("Cancel");
            }
        }

        int bytesRead = super.read(b);

        if (bytesRead > 0) {
            uploadedBytes += bytesRead;
            reportProgress();
        }

        return bytesRead;
    }


    public UploadInputStream(File file, UploadHook hook, long totalSize, long historySize) throws FileNotFoundException {
        super(file);
        if (hook != null) {
            this.hook = hook;
            this.meta = hook.getTaskMeta();
        }
        this.totalBytes = totalSize;
        this.historySize = historySize;
    }


    private long lastUploadedBytes = historySize;

    private static final double ALPHA = 0.2; // 平滑系数
    private double smoothedSpeedKBps = 0.0;

    private void reportProgress() {
        if (meta != null) {
            // 计算进度百分比
            double progressPercent = (uploadedBytes + historySize) * 100.0 / totalBytes;

            long intervalBytes = (uploadedBytes + historySize) - (lastUploadedBytes + historySize);
            double intervalSeconds = (System.currentTimeMillis() - meta.getLastUpdateTime()) / 1000.0;
            if (intervalSeconds < 1){
                return;
            }

            double currentSpeedKBps = intervalBytes / 1024.0 / intervalSeconds;
            smoothedSpeedKBps = ALPHA * currentSpeedKBps + (1 - ALPHA) * smoothedSpeedKBps;

            // 更新记录
            lastUploadedBytes = uploadedBytes;
            meta.setLastUpdateTime(System.currentTimeMillis());

            meta.setProgress(df.format(progressPercent));
            meta.setElapsed(df.format(smoothedSpeedKBps)); // 显示平滑速度
        }
    }

}
