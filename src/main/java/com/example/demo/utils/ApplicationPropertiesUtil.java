package com.example.demo.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 供独立 main 方法读取 application.properties。
 * 这些类不在 Spring 容器里，需要手动加载运行配置。
 */
public final class ApplicationPropertiesUtil {

    private static final String APPLICATION_PROPERTIES_FILE = "application.properties";

    private ApplicationPropertiesUtil() {
        // 工具类不需要实例化。
    }

    public static Properties loadApplicationProperties() throws IOException {
        InputStream inputStream =
            ApplicationPropertiesUtil.class
                .getClassLoader()
                .getResourceAsStream(APPLICATION_PROPERTIES_FILE);
        if (inputStream == null) {
            throw new IOException("未找到配置文件: " + APPLICATION_PROPERTIES_FILE);
        }

        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } finally {
            inputStream.close();
        }
        return properties;
    }

    public static String getRequiredProperty(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("缺少配置项: " + key);
        }
        return value.trim();
    }

    public static int getRequiredIntProperty(Properties properties, String key) throws IOException {
        String value = getRequiredProperty(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("配置项不是有效整数: " + key + "=" + value, e);
        }
    }
}
