package team.terrafirmagreg.autopack.core.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;

/**
 * Utility class to provide Jackson functionality that works in both standalone and universal JAR scenarios.
 * In the universal JAR, Jackson classes are relocated to avoid module conflicts.
 */
public class JacksonProvider {
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
    
    private static ObjectMapper createObjectMapper() {
        try {
            // Try to use the relocated Jackson classes first (for universal JAR)
            Class<?> relocatedObjectMapperClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.databind.ObjectMapper");
            ObjectMapper instance = (ObjectMapper) relocatedObjectMapperClass.getDeclaredConstructor().newInstance();
            instance.setDefaultLeniency(false);
            
            // Set visibility using reflection to handle relocated classes
            Class<?> propertyAccessorClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.annotation.PropertyAccessor");
            Class<?> jsonAutoDetectClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.annotation.JsonAutoDetect");
            
            Object allProperty = propertyAccessorClass.getField("ALL").get(null);
            Object noneVisibility = jsonAutoDetectClass.getField("NONE").get(null);
            
            instance.getClass().getMethod("setVisibility", propertyAccessorClass, jsonAutoDetectClass.getField("Visibility").getType())
                .invoke(instance, allProperty, noneVisibility);
            
            return instance;
        } catch (Exception e) {
            // Fallback to regular Jackson classes (for standalone or when relocation fails)
            try {
                ObjectMapper instance = new ObjectMapper();
                instance.setDefaultLeniency(false);
                instance.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
                return instance;
            } catch (Exception fallbackException) {
                throw new RuntimeException("Failed to initialize Jackson ObjectMapper. " +
                    "Make sure Jackson is available in the classpath.", fallbackException);
            }
        }
    }
} 