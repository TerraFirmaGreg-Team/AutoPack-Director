package team.terrafirmagreg.autopack.core.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
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
            Class<?> relocatedObjectMapperClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.databind.ObjectMapper");
            ObjectMapper instance = (ObjectMapper) relocatedObjectMapperClass.getDeclaredConstructor().newInstance();
            instance.setDefaultLeniency(false);

            Class<?> propertyAccessorClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.annotation.PropertyAccessor");
            Class<?> jsonAutoDetectClass = Class.forName("team.terrafirmagreg.autopack.shadow.jackson.annotation.JsonAutoDetect");

            Object allProperty = propertyAccessorClass.getField("ALL").get(null);
            Object noneVisibility = jsonAutoDetectClass.getField("NONE").get(null);

            instance.getClass().getMethod("setVisibility", propertyAccessorClass, jsonAutoDetectClass.getField("Visibility").getType())
                .invoke(instance, allProperty, noneVisibility);

            configureDeserializationFeaturesRelocated(instance);
            return instance;
        } catch (Exception e) {
            try {
                ObjectMapper instance = new ObjectMapper();
                instance.setDefaultLeniency(false);
                instance.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
                configureDeserializationFeatures(instance);
                return instance;
            } catch (Exception fallbackException) {
                throw new RuntimeException("Failed to initialize Jackson ObjectMapper. " +
                    "Make sure Jackson is available in the classpath.", fallbackException);
            }
        }
    }

    private static void configureDeserializationFeatures(ObjectMapper instance) {
        instance.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        instance.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        instance.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
    }

    private static void configureDeserializationFeaturesRelocated(ObjectMapper instance) throws Exception {
        Class<?> featureClass = Class.forName(
            "team.terrafirmagreg.autopack.shadow.jackson.databind.DeserializationFeature");
        Object failUnknown = featureClass.getField("FAIL_ON_UNKNOWN_PROPERTIES").get(null);
        Object failMissing = featureClass.getField("FAIL_ON_MISSING_CREATOR_PROPERTIES").get(null);
        Object failNull = featureClass.getField("FAIL_ON_NULL_CREATOR_PROPERTIES").get(null);
        var enable = instance.getClass().getMethod("enable", featureClass);
        enable.invoke(instance, failUnknown);
        enable.invoke(instance, failMissing);
        enable.invoke(instance, failNull);
    }
}
