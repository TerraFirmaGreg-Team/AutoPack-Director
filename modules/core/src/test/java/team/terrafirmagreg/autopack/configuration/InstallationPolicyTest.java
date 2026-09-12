package team.terrafirmagreg.autopack.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstallationPolicyTest {

    @Test
    void supersedesListTakesPriority() {
        List<String> patterns = Arrays.asList("old-*.jar", "legacy-*.jar");
        InstallationPolicy policy = InstallationPolicy.builder()
                .supersede("single.jar")
                .supersedes(patterns)
                .build();

        assertEquals(patterns, policy.allSupersedePatterns());
    }

    @Test
    void singleSupersedeFallsBackWhenListEmpty() {
        InstallationPolicy policy = InstallationPolicy.builder()
                .supersede("single.jar")
                .supersedes(Collections.emptyList())
                .build();

        assertEquals(Collections.singletonList("single.jar"), policy.allSupersedePatterns());
    }

    @Test
    void noSupersedeReturnsEmptyList() {
        InstallationPolicy policy = InstallationPolicy.builder().build();

        assertTrue(policy.allSupersedePatterns().isEmpty());
    }

    @Test
    void selectedByDefaultDefaultsToTrueWhenOptionalKeyPresent() {
        InstallationPolicy policy = InstallationPolicy.builder()
                .optionalKey("optional-group")
                .name("Optional")
                .description("desc")
                .build();

        assertTrue(policy.isSelectedByDefault());
    }

    @Test
    void selectedByDefaultCanBeExplicitlyFalse() {
        InstallationPolicy policy = InstallationPolicy.builder()
                .optionalKey("optional-group")
                .selectedByDefault(false)
                .name("Optional")
                .description("desc")
                .build();

        assertFalse(policy.isSelectedByDefault());
    }

    @Test
    void continueOnFailedDownloadFlag() {
        InstallationPolicy policy =
                InstallationPolicy.builder().continueOnFailedDownload(true).build();

        assertTrue(policy.continueOnFailedDownload());
    }
}
