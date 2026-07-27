package team.terrafirmagreg.autopack.core.manage.install;

import com.github.bsideup.jabel.Desugar;

import java.nio.file.Path;
import java.util.Map;

@Desugar
public record InstalledMod(Path file, boolean inject, Map<String, Object> options) {

    public InstalledMod(Path file, Map<String, Object> options, boolean inject) {
        this(file, inject, options);
    }

    public boolean getOptionBoolean(String key, boolean defaultValue) {
        if (!options.containsKey(key)) {
            return defaultValue;
        }

        Object object = options.get(key);
        if (object instanceof Boolean bol) {
            return bol;
        }

        throw new IllegalArgumentException("Option " + key + " for mod file " + file.toString() + " should have been " +
                "a boolean, but found " + object.getClass().getName());
    }
}
