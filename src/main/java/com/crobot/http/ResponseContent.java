package com.crobot.http;

import org.apache.http.Header;

import java.io.Serializable;
import java.util.Arrays;

public class ResponseContent implements Serializable {
    private static final long serialVersionUID = -7046458118346446443L;

    private Header[] headers;
    private int responseCode;
    private String content;

    public Header[] getHeaders() {
        return headers;
    }

    public void setHeaders(Header[] headers) {
        this.headers = headers;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "Response Headers:" + Arrays.toString(headers) + "\n" + "Response Code：" + responseCode + "\n" + "Response Content：" + content;
    }

}