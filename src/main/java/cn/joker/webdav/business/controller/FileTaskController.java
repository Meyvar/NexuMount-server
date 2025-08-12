package cn.joker.webdav.business.controller;

import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.result.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sysFileTask")
public class FileTaskController {



    @Autowired
    private TaskManager taskManager;

    /**
     * 暂停
     *
     * @param list
     * @return
     */
    @PostMapping("/pause.do")
    public Response<String> pause(@RequestBody List<String> list) {
        list.forEach(taskManager::pauseTask);
        return Response.success();
    }

    /**
     * 恢复
     *
     * @param list
     * @return
     */
    @PostMapping("/resume.do")
    public Response<String> resume(@RequestBody List<String> list) {
        list.forEach(taskManager::resumeTask);
        return Response.success();
    }


    /**
     * 取消
     * @param list
     * @return
     */
    @PostMapping("/cancel.do")
    public Response<String> cancel(@RequestBody List<String> list) {
        list.forEach(taskManager::cancelTask);
        return Response.success();
    }


    /**
     * 进行中列表
     * @return
     */
    @GetMapping("/running.do")
    public Response<List<TaskMeta>> getRunningTasks() {
        return Response.success(taskManager.getRunningTasks());
    }


    /**
     * 结束列表
     * @return
     */
    @GetMapping("/completed.do")
    public  Response<List<TaskMeta>> getCompletedTasks() {
        return Response.success(taskManager.getCompletedTasks());
    }

    /**
     * 删除已完成任务
     * @return
     */
    @GetMapping("/removeAllCompletedTasks.do")
    public  Response<String> removeAllCompletedTasks() {
        taskManager.removeAllCompletedTasks();
        return Response.success();
    }

    /**
     * 重启失败的任务
     * @return
     */
    @PostMapping("/restartFailedTask.do")
    public Response<String> restartFailedTask(@RequestBody List<String> list) {
        list.forEach(taskManager::restartFailedTask);
        return Response.success();
    }

    /**
     * 删除任务
     * @return
     */
    @PostMapping("/removeTask.do")
    public Response<String> removeTask(@RequestBody List<String> list) {
        list.forEach(taskManager::removeTask);
        return Response.success();
    }
}
