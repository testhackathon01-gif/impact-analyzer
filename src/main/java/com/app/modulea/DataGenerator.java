package com.app.modulea;

public class DataGenerator {
    public String generateData() {
        A_Helper h = new A_Helper();
        String ts = h.getTimestamp();
        return "Data:" + ts;
    }
}
