package cn.joker.webdav.business.result;

public class Page<T> extends com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> {

    private long page;


    public Page(long page, long limit){
        this.setCurrent(page);
        this.setSize(limit);
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }
}
