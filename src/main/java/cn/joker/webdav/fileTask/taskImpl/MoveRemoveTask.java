package cn.joker.webdav.fileTask.taskImpl;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.FileTransferTask;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.fileTask.TaskStatus;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MoveRemoveTask extends FileTransferTask {

    public MoveRemoveTask(String taskId, FileBucket fromBucket, String fromPath) {
        this.taskId = taskId;
        this.fromBucket = fromBucket;
        this.fromPath = fromPath;
    }

    @Override
    public void taskContent(TaskManager tm, TaskMeta meta) throws Exception {
        // 检查任务是否被取消
        if (tm.isCancelled(meta.getTaskId())) {
            meta.setStatus(TaskStatus.CANCELLED);
            return;
        }

        meta.setProgress("0");

        String describe = "remove " + fromPath;

        meta.setDescribe(describe + " (Waiting to delete...)");

        IFileAdapter fromAdapter = SprintContextUtil.getBean(fromBucket.getAdapter(), IFileAdapter.class);

        String path = PathUtils.normalizePath("/" + fromPath.replaceFirst(fromBucket.getSourcePath(), ""));

        if (fromAdapter.getFolderItself(fromBucket, path).getType().equals("folder")) {

            List<FileResource> fileResourceList = fromAdapter.propFind(fromBucket, path, true);
            if (fileResourceList == null || fileResourceList.isEmpty()) {
                fromAdapter.delete(fromBucket, fromPath);
                meta.setDescribe(describe + " (Success)");
            } else {
                meta.setStatus(TaskStatus.PAUSED);
                meta.setDescribe(describe + "(Waiting for subfiles to complete...)");

                tm.getScheduledExecutorService().schedule(() -> {
                    meta.setStatus(TaskStatus.RUNNING);
                    tm.getExecutor().submit(MoveRemoveTask.this);
                }, 2, TimeUnit.SECONDS);
            }

        } else {
            fromAdapter.delete(fromBucket, fromPath);
        }

    }
}
