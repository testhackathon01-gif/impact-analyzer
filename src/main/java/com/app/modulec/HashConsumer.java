package com.app.modulec;

import com.app.modulea.A_Helper;

public class HashConsumer {
    public void run() {
        A_Helper h = new A_Helper();
        int result = h.calculateHash(10, 5);
        System.out.println("Hash: " + result);
    }
}