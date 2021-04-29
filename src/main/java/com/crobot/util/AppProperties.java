package com.crobot.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Properties;

@Slf4j
public class AppProperties {
    private static final AppProperties INSTANCE = new AppProperties();
    private static final Properties properties = new Properties();

    private AppProperties() {
    }

    public static AppProperties getInstance() {
        return AppProperties.INSTANCE;
    }

    public Properties getProperties() {
        return properties;
    }

    public void init() {
        log.debug("Loading properties");
        try {
            properties.load(getClass().getResourceAsStream("/application.properties"));
        } catch (final IOException e) {
            log.error("Failed to load the properties");
        }
    }

    /**
     * Returns value of teh property.
     *
     * @param property
     * @return
     */
    public String getProperty(String property) {
        return properties.getProperty(property);
    }
}
