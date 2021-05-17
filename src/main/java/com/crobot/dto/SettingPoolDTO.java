package com.crobot.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SettingPoolDTO implements Serializable {
    private static final long serialVersionUID = 1164215696874450621L;

    private Long id;

    private String definitionType;

    private Long definitionId;

    private Integer orderNumber;

    private Integer year;

    private Integer verdictNoStart;

    private Integer verdictNoEnd;

    private String status;
}
