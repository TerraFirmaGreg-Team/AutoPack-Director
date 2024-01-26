package com.juanmuscaria.modpackdirector.i18n;

import com.juanmuscaria.modpackdirector.util.PlatformDelegate;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Properties;

public class Messages {
    private final Properties messages = new Properties();
    private final PlatformDelegate platform;

    public Messages(PlatformDelegate platform, boolean loadUserMessages) {
        this.platform = platform;
        try {
            messages.loadFromXML(this.getClass().getResourceAsStream("/modpack_director.xml"));
        } catch (Exception e) {
            platform.logger().warn("Unable to load builtin messages", e);
        }
        if (loadUserMessages) {
            var userOverrides = platform.configurationDirectory().resolve("messages.xml");
            if (Files.isRegularFile(userOverrides)) {
                try {
                    messages.loadFromXML(Files.newInputStream(userOverrides));
                } catch (Exception e) {
                    platform.logger().warn("Unable to load user messages", e);
                }
            } else {
                try {
                    messages.storeToXML(Files.newOutputStream(userOverrides), "Use this file to overwrite UI texts");
                } catch (IOException e) {
                    platform.logger().warn("Unable to create user messages", e);
                }
            }
        }
    }

    public String get(String key, Object... params) {
        try {
            if (messages.containsKey(key)) {
                return String.format(messages.getProperty(key), params);
            }
        } catch (IllegalFormatException e) {
            platform.logger().warn("Unable to format key {0} due to bad expression", key, e);
        }
        if (params.length > 0) {
            return key + ':' + Arrays.toString(params);
        } else {
            return key;
        }
    }
}
