package com.squirrelvim.engine;

public record Register(String text, boolean lineWise) {
    public static final Register EMPTY = new Register("", false);
}
