package org.earburn.client.library.util;

public enum StringFormat {
    BLACK("\u000301"),
    BLUE("\u000312"),
    BOLD("\u0002"),
    BROWN("\u000305"),
    CYAN("\u000311"),
    DARK_BLUE("\u000302"),
    DARK_GRAY("\u000314"),
    DARK_GREEN("\u000303"),
    GREEN("\u000309"),
    LIGHT_GRAY("\u000315"),
    MAGENTA("\u000313"),
    RESET("\u000f"),
    OLIVE("\u000307"),
    PURPLE("\u000306"),
    RED("\u000304"),
    REVERSE("\u0016"),
    TEAL("\u000310"),
    UNDERLINE("\u001f"),
    WHITE("\u000300"),
    YELLOW("\u000308");

    private String string;

    private StringFormat(String stringToFormat) {
        this.string = stringToFormat;
    }

    @Override
    public String toString(){
        return this.string;
    }

}