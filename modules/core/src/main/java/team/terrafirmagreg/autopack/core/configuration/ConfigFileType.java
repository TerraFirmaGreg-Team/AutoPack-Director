package team.terrafirmagreg.autopack.core.configuration;

import lombok.Getter;
import team.terrafirmagreg.autopack.core.configuration.modpack.ModpackConfiguration;
import team.terrafirmagreg.autopack.core.configuration.type.CurseRemoteMod;
import team.terrafirmagreg.autopack.core.configuration.type.ModifyMod;
import team.terrafirmagreg.autopack.core.configuration.type.ModrinthRemoteMod;
import team.terrafirmagreg.autopack.core.configuration.type.RemoteConfig;
import team.terrafirmagreg.autopack.core.configuration.type.UrlRemoteMod;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public enum ConfigFileType {
    CURSE(".curse.json", "curse", CurseRemoteMod.class, true),
    MODRINTH(".modrinth.json", "modrinth", ModrinthRemoteMod.class, true),
    URL(".url.json", "url", UrlRemoteMod.class, true),
    MODIFY(".modify.json", "modify", ModifyMod.class, true),
    REMOTE(".remote.json", "remote", RemoteConfig.class, false),
    BUNDLE(".bundle.json", "bundle", null, false),
    MODPACK("modpack.json", "modpack", ModpackConfiguration.class, false);

    private static final List<ConfigFileType> BUNDLE_ENTRY_TYPES = Collections.unmodifiableList(
        Arrays.asList(CURSE, MODRINTH, URL, MODIFY));

    private final String suffix;
    private final String schemaType;
    private final Class<?> modelClass;
    private final boolean bundleEntry;

    ConfigFileType(String suffix, String schemaType, Class<?> modelClass, boolean bundleEntry) {
        this.suffix = suffix;
        this.schemaType = schemaType;
        this.modelClass = modelClass;
        this.bundleEntry = bundleEntry;
    }

    public String schemaResource() {
        return "/schemas/" + schemaType + ".schema.json";
    }

    public String fileName(String baseName) {
        return baseName + suffix;
    }

    public boolean isRemoteMod() {
        return modelClass != null && RemoteMod.class.isAssignableFrom(modelClass);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends RemoteMod> remoteModClass() {
        if (!isRemoteMod()) {
            return null;
        }
        return (Class<? extends RemoteMod>) modelClass;
    }

    public static List<ConfigFileType> bundleEntryTypes() {
        return BUNDLE_ENTRY_TYPES;
    }

    public static ConfigFileType fromPakkuPlatform(String platform) {
        if ("curseforge".equals(platform)) {
            return CURSE;
        }
        if ("modrinth".equals(platform)) {
            return MODRINTH;
        }
        return null;
    }

    public static boolean isModpackFileName(String fileName) {
        return MODPACK.suffix.equals(fileName);
    }

    public static ConfigFileType fromPath(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        if (name.equals(MODPACK.suffix)) {
            return MODPACK;
        }
        for (ConfigFileType type : values()) {
            if (type != MODPACK && name.endsWith(type.suffix)) {
                return type;
            }
        }
        return null;
    }
}
