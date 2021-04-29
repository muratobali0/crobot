package com.crobot.http;

import lombok.Data;

import java.io.Serializable;

@Data
public class CaptchaRequestDTO implements Serializable {
    private static final long serialVersionUID = -6572744626488344034L;

    private String userid;

    private String apikey;

    private String data;

}