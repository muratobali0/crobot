package com.crobot.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SettingDTO implements Serializable {
    private static final long serialVersionUID = 6283948556765741885L;

    private Long id;

    private String webPageUrl;

    private String captchaServiceUrl;

    private String captchaServiceUserid;

    private String captchaServiceApikey;

    private Integer fairDuration;

}
