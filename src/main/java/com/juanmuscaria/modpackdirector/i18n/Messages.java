package com.juanmuscaria.modpackdirector.i18n;

import com.juanmuscaria.autumn.messages.HierarchicalMessageSource;
import com.juanmuscaria.autumn.messages.NoSuchMessageException;
import com.juanmuscaria.autumn.messages.standard.ReloadableResourceBundleMessageSource;
import com.juanmuscaria.autumn.resources.DefaultResourceLoader;
import com.juanmuscaria.autumn.resources.FileSystemResourceLoader;
import com.juanmuscaria.modpackdirector.util.PlatformDelegate;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Locale;

public class Messages {
    private final HierarchicalMessageSource messages;
    private final PlatformDelegate platform;
    private @Setter
    @Getter Locale userLocale = Locale.getDefault();

    public Messages(PlatformDelegate platform, boolean loadUserMessages) {
        this.platform = platform;
        var src = new ReloadableResourceBundleMessageSource();
        src.setBasename("classpath:com/juanmuscaria/modpackdirector/i18n/messages");
        src.setDefaultEncoding("UTF-8");
        src.setResourceLoader(new DefaultResourceLoader(this.getClass().getClassLoader()));

        if (loadUserMessages) {
            var external = new ReloadableResourceBundleMessageSource();
            external.setResourceLoader(new FileSystemResourceLoader());
            external.setBasename(platform.configurationDirectory().toUri() + "/messages");
            external.setDefaultEncoding("UTF-8");
            external.setParentMessageSource(src);
            src = external;
        }

        this.messages = src;
    }

    public String get(String key, Object... params) {
        try {
            return messages.getMessage(key, params, userLocale);
        } catch (IllegalFormatException e) {
            platform.logger().warn("Unable to format key {0} due to bad expression", key, e);
        } catch (NoSuchMessageException ignored) {
        }

        if (params.length > 0) {
            return key + ':' + Arrays.toString(params);
        } else {
            return key;
        }
    }
}
