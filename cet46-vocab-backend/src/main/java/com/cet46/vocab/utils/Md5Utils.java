package com.cet46.vocab.utils;

import cn.hutool.crypto.digest.DigestUtil;

public class Md5Utils {

    private Md5Utils() {
    }

    public static String md5(String input) {
        return DigestUtil.md5Hex(input);
    }
}
