package com.docstar.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class PropertyLoaderConfig {

    // Spring injects the resource loader
    private final ResourceLoader resourceLoader;

    public PropertyLoaderConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    // This bean loads the properties file and makes the Properties object available
    @Bean
    public Properties commonProperties() throws IOException {
        // Specify the path relative to the classpath (i.e., the resources folder)
        Resource resource = resourceLoader.getResource("classpath:config/Common.properties");

        System.out.println("Loading common properties from: " + resource.getURI());

        Properties props = new Properties();
        // Load the properties from the file stream
        props.load(resource.getInputStream());
        System.out.println("Common properties loaded: " + props);
        return props;
    }

    @Bean
    public Properties outsourceProperties() throws IOException {
        // Specify the path relative to the classpath (i.e., the resources folder)
        Resource resource = resourceLoader.getResource("classpath:config/Outsource.properties");

        Properties props = new Properties();
        // Load the properties from the file stream
        props.load(resource.getInputStream());
        return props;
    }

    @Bean
    public Properties dboProperties() throws IOException {
        // Specify the path relative to the classpath (i.e., the resources folder)
        Resource resource = resourceLoader.getResource("classpath:config/DBO.properties");

        Properties props = new Properties();
        // Load the properties from the file stream
        props.load(resource.getInputStream());
        return props;
    }
}