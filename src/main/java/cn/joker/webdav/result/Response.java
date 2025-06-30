package cn.joker.webdav.result;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;

public class Response<T> {

    /**
     * 请求成功
     */
    public static final int CODE_SUCCESS = 0;

    /**
     * 请求出错
     */
    public static final int CODE_ERROR = 1;

    /**
     * 未登录
     */
    public static final int NO_LOGIN = 401;


    /**
     * 权限不足
     */
    public static final int NO_PERMISSIONS = 403;

    /**
     * 请求状态码
     */
    private int code;
    /**
     * 返回信息
     */
    private String msg;
    /**
     * 返回数据
     */
    private T data;
    /**
     * 返回数据长度
     */
    private long count;
    /**
     * 请求状态
     */
    private boolean success;

    public Response() {
        this.setSuccess(true);
        this.setCode(Response.CODE_SUCCESS);
    }

    public static Response error(String errMsg) {
        Response response = new Response();
        response.setSuccess(false);
        response.setCode(Response.CODE_ERROR);
        response.setMsg(errMsg);
        return response;
    }

    public static Response error(String errMsg, String errLog) {
        Response<String> response = new Response<String>();
        response.setSuccess(false);
        response.setCode(Response.CODE_ERROR);
        response.setMsg(errMsg);
        response.setData(errLog);
        return response;
    }

    public static Response success(String msg) {
        Response response = new Response();
        response.setSuccess(true);
        response.setCode(Response.CODE_SUCCESS);
        response.setMsg(msg);
        return response;
    }

    public static Response success() {
        Response response = new Response();
        response.setSuccess(true);
        response.setCode(Response.CODE_SUCCESS);
        response.setMsg("操作成功");
        return response;
    }

    public static <K> Response<K> success(String msg, K data) {
        Response<K> response = new Response<K>();
        response.setSuccess(true);
        response.setCode(Response.CODE_SUCCESS);
        response.setMsg(msg);
        response.setData(data);
        return response;
    }

    public static <K> Response<K> success(K data) {
        Response<K> response = new Response<K>();
        response.setSuccess(true);
        response.setCode(Response.CODE_SUCCESS);
        response.setData(data);
        return response;
    }

    public void setPage(IPage iPage) {
        this.setData((T) iPage.getRecords());
        this.setCount(iPage.getTotal());
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
