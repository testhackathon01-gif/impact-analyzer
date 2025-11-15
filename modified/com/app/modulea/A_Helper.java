package com.app.modulea;

public class A_Helper {
    public long getTimestamp() {
        return System.currentTimeMillis();
    }

    public int calculateHash(int a, int b) {
        int product = a * b;
        return product / 2;
    }
}
