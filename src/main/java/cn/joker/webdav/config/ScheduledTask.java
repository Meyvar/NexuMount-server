package cn.joker.webdav.config;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.IFileBucketService;
import cn.joker.webdav.database.service.IKeyValueService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScheduledTask implements ApplicationRunner {

    @Autowired
    private IKeyValueService keyValueService;

    @Autowired
    private IFileBucketService fileBucketService;

    public void createFileBucketStatus() {
        // 启动时执行一次
        List<FileBucket> list = keyValueService.findBusinessAll("fileBucket", FileBucket.class);
        list.removeIf(item -> "disable".equals(item.getStatus()));
        fileBucketService.updateFileBucketStatus(list.stream().map(FileBucket::getUuid).toList());
    }

    @Scheduled(fixedRate = 12 * 60 * 60 * 1000)
    public void updateFileBucketStatus() {
        createFileBucketStatus();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createFileBucketStatus();
    }
}
