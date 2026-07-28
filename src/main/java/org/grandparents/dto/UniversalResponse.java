package org.grandparents.dto;

import java.util.List;

public class UniversalResponse {
    private String text;

    public UniversalResponse() {}

    public UniversalResponse(String text) {
        this.text = text;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    @Override
    public String toString() {
        return "UniversalResponse{" +
                "text='" + text + '\'' +
                '}';
    }
}