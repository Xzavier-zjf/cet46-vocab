package com.cet46.vocab.llm;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class CloudApiKeyCipher {

    private static final String DEFAULT_SECRET = "cet46-cloud-key-secret-change-me";

    @Value("${llm.cloud.key-secret:}")
    private String keySecret;

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        AES aes = SecureUtil.aes(resolveAesKey());
        return aes.encryptBase64(plainText.trim());
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            AES aes = SecureUtil.aes(resolveAesKey());
            String value = aes.decryptStr(cipherText.trim(), StandardCharsets.UTF_8);
            return StringUtils.hasText(value) ? value.trim() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public String mask(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        String value = plainText.trim();
        int len = value.length();
        if (len <= 4) {
            return "****";
        }
        if (len <= 8) {
            return value.substring(0, 1) + "***" + value.substring(len - 1);
        }
        return value.substring(0, 3) + "****" + value.substring(len - 3);
    }

    private byte[] resolveAesKey() {
        String source = StringUtils.hasText(keySecret)
                ? keySecret.trim()
                : firstNonBlank(System.getenv("LLM_CLOUD_KEY_SECRET"), System.getProperty("LLM_CLOUD_KEY_SECRET"), DEFAULT_SECRET);
        byte[] hash = SecureUtil.sha256(source).getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(hash, 16);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
