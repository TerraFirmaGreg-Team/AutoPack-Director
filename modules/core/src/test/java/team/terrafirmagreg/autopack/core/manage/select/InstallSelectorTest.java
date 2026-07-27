package team.terrafirmagreg.autopack.core.manage.select;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.autopack.core.configuration.InstallationPolicy;
import team.terrafirmagreg.autopack.core.configuration.RemoteModInformation;
import team.terrafirmagreg.autopack.core.manage.install.InstallableMod;
import team.terrafirmagreg.autopack.testsupport.TestRemoteMod;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallSelectorTest {

    @TempDir
    Path tempDir;

    @Test
    void modsWithoutOptionalKeyAlwaysInstall() {
        InstallSelector selector = new InstallSelector();
        InstallableMod mod = installable("required.jar", null);

        selector.accept(Collections.emptyList(), Collections.singletonList(mod), Collections.emptyList());

        assertFalse(selector.hasSelectableOptions());
        assertEquals(1, selector.computeModsToInstall().size());
        assertTrue(selector.computeDisabledMods().isEmpty());
    }

    @Test
    void singleOptionalModCreatesSingleOption() {
        InstallSelector selector = new InstallSelector();
        InstallableMod mod = installable("optional.jar", optionalPolicy("$", true));

        selector.accept(Collections.emptyList(), Collections.singletonList(mod), Collections.emptyList());

        assertTrue(selector.hasSelectableOptions());
        assertEquals(1, selector.getSingleOptions().size());
        assertEquals(1, selector.computeModsToInstall().size());
    }

    @Test
    void groupedOptionalModsShareGroup() {
        InstallSelector selector = new InstallSelector();
        InstallableMod first = installable("opt-a.jar", optionalPolicy("group-a", true));
        InstallableMod second = installable("opt-b.jar", optionalPolicy("group-a", false));

        selector.accept(Collections.emptyList(), Arrays.asList(first, second), Collections.emptyList());

        assertTrue(selector.getGroupOptions().containsKey("group-a"));
        assertEquals(2, selector.getGroupOptions().get("group-a").size());
    }

    @Test
    void excludedGroupIsIgnoredForFreshInstalls() {
        InstallSelector selector = new InstallSelector();
        TestRemoteMod excluded = TestRemoteMod.builder()
            .name("excluded")
            .installationPolicy(optionalPolicy("blocked-group", true))
            .build();
        InstallableMod fresh = installable("fresh.jar", optionalPolicy("blocked-group", true));

        selector.accept(Collections.singletonList(excluded), Collections.singletonList(fresh), Collections.emptyList());

        assertFalse(selector.hasSelectableOptions());
        assertTrue(selector.computeModsToInstall().isEmpty());
    }

    @Test
    void reinstallAddsToAlwaysInstallAndBlocksGroup() {
        InstallSelector selector = new InstallSelector();
        TestRemoteMod reinstallRemote = TestRemoteMod.builder()
            .name("reinstall")
            .installationPolicy(optionalPolicy("blocked-group", true))
            .build();
        InstallableMod reinstall = new InstallableMod(
            reinstallRemote,
            new RemoteModInformation("Reinstall", "reinstall.jar"),
            tempDir.resolve("reinstall.jar")
        );
        InstallableMod fresh = installable("fresh.jar", optionalPolicy("blocked-group", true));

        selector.accept(Collections.emptyList(), Collections.singletonList(fresh), Collections.singletonList(reinstall));

        assertEquals(1, selector.computeModsToInstall().size());
        assertFalse(selector.hasSelectableOptions());
    }

    @Test
    void unselectedOptionalModIsDisabled() {
        InstallSelector selector = new InstallSelector();
        InstallableMod mod = installable("optional.jar", optionalPolicy("$", false));

        selector.accept(Collections.emptyList(), Collections.singletonList(mod), Collections.emptyList());

        assertTrue(selector.computeModsToInstall().isEmpty());
        assertEquals(1, selector.computeDisabledMods().size());
    }

    private static InstallableMod installable(String fileName, InstallationPolicy policy) {
        TestRemoteMod remoteMod = TestRemoteMod.builder()
            .name(fileName)
            .installationPolicy(policy)
            .information(new RemoteModInformation(fileName, fileName))
            .build();
        return new InstallableMod(remoteMod, new RemoteModInformation(fileName, fileName), Paths.get(fileName));
    }

    private static InstallationPolicy optionalPolicy(String optionalKey, boolean selectedByDefault) {
        return InstallationPolicy.builder()
            .optionalKey(optionalKey)
            .selectedByDefault(selectedByDefault)
            .name(optionalKey)
            .description("description")
            .build();
    }
}
