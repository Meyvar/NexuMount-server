package cn.joker.webdav.utils.fileUpload;

import cn.joker.webdav.fileTask.UploadHook;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ProgressRequestBody extends RequestBody {
    private final File file;
    private final UploadInputStream inputStream;

    public ProgressRequestBody(File file, long totalSize, long historySize, long nowNs, UploadHook hook) throws FileNotFoundException {
        this.file = file;
        this.inputStream = new UploadInputStream(file, hook, totalSize, historySize, nowNs);
    }

    @Override
    public MediaType contentType() {
        return MediaType.parse("application/octet-stream");
    }

    @Override
    public long contentLength() {
        return file.length();
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            sink.write(buffer, 0, bytesRead);
        }
    }
}
