package com.crobot.page;

public enum DaireType {
    KURUL(0),
    CEZA(1),
    HUKUK(2);

    private int value;

    DaireType(int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

}
