package com.crobot.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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
     * Returns String value of the property.
     *
     * @param property
     * @return
     */
    public String getProperty(String property) {
        return properties.getProperty(property);
    }

    /**
     * Returns int value of the property
     *
     * @param property
     * @return
     */
    public int getPropertyAsInt(String property) {
        return Integer.parseInt(properties.getProperty(property));
    }

    /**
     * Converts property and returns list of integers
     *
     * @param property
     * @return
     */
    public List<Integer> getPropertyAsIntegerList(String property) {
        List<String> strings = Arrays.asList(properties.getProperty(property).split(","));
        List<Integer> list = strings.stream().map(i -> Integer.parseInt(i.trim())).collect(Collectors.toList());
        return list;
    }

    /**
     *
     * @param property
     * @return
     */
    public List<String> getPropertyAsStringList(String property) {
        return Arrays.asList(properties.getProperty(property).split(","));
    }
}
