package com.crobot.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DocumentDTO implements Serializable {
    private static final long serialVersionUID = 77775665158054718L;

    private String documentName;

    private Integer verdictYear;

    private String definitionType;

    private byte[] documentData;
}