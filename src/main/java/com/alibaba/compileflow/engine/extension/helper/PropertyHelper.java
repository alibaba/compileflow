package com.alibaba.compileflow.engine.extension.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

/**
 * @author yusu
 */
public class PropertyHelper {

    public static boolean getBooleanProperty(final String key, final boolean defaultValue) {
        String value = System.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public static String getProperty(final String key, final String defaultValue) {
        String value = System.getProperty(key);
        return value != null ? value : defaultValue;
    }

    public static String getProperty(final String key) {
        return System.getProperty(key);
    }

    public static Properties getProperties(InputStream inputStream) {
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return properties;
    }

    public static Properties getProperties(URL url) {
        try {
            return getProperties(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
