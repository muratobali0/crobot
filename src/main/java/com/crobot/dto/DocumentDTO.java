package com.crobot.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class DocumentDTO implements Serializable {
    private static final long serialVersionUID = 978638062354663308L;

    private String documentName;

    private Integer verdictYear;

    private String definitionType;

    private byte[] data;
}