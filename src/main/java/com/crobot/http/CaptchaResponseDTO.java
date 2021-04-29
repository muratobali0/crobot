package com.crobot.http;

import lombok.Data;

import java.io.Serializable;

@Data
public class CaptchaResponseDTO implements Serializable {
    private static final long serialVersionUID = 8657191808862088922L;

    private String result;

    private String conf;

    private String requestId;

}