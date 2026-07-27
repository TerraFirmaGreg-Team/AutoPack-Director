package team.terrafirmagreg.autopack.core.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.autopack.core.util.HashResult;
import team.terrafirmagreg.autopack.testsupport.TestPlatform;
import team.terrafirmagreg.autopack.util.Side;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteModMetadataTest {

    @TempDir
    Path tempDir;

    @Test
    void nullHashesReturnUnknown() {
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .side(Side.UNKNOWN)
            .build();
        Path file = tempDir.resolve("mod.jar");

        assertEquals(HashResult.UNKNOWN, metadata.checkHashes(file, new TestPlatform(tempDir)));
    }

    @Test
    void matchingMd5ReturnsMatched(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mod.jar");
        Files.write(file, "hello".getBytes());

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "5d41402abc4b2a76b9719d911017c592");
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .hashes(hashes)
            .side(Side.UNKNOWN)
            .build();

        assertEquals(HashResult.MATCHED, metadata.checkHashes(file, new TestPlatform(dir)));
    }

    @Test
    void mismatchedMd5ReturnsUnmatched(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mod.jar");
        Files.write(file, "hello".getBytes());

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "00000000000000000000000000000000");
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .hashes(hashes)
            .side(Side.UNKNOWN)
            .build();

        assertEquals(HashResult.UNMATCHED, metadata.checkHashes(file, new TestPlatform(dir)));
    }

    @Test
    void missingFileReturnsUnmatched() {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("MD5", "5d41402abc4b2a76b9719d911017c592");
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .hashes(hashes)
            .build();

        assertEquals(HashResult.UNMATCHED,
            metadata.checkHashes(tempDir.resolve("missing.jar"), new TestPlatform(tempDir)));
    }

    @Test
    void unsupportedAlgorithmReturnsUnknown(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mod.jar");
        Files.write(file, "hello".getBytes());

        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("NOT_A_REAL_HASH_ALG", "abc");
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .hashes(hashes)
            .build();

        assertEquals(HashResult.UNKNOWN, metadata.checkHashes(file, new TestPlatform(dir)));
    }

    @Test
    void shouldTryInstallWhenSideMatches() {
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .side(Side.CLIENT)
            .build();

        assertTrue(metadata.shouldTryInstall(new TestPlatform(tempDir, Side.CLIENT)));
        assertFalse(metadata.shouldTryInstall(new TestPlatform(tempDir, Side.SERVER)));
    }

    @Test
    void shouldTryInstallWhenSideUnknown() {
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .side(Side.UNKNOWN)
            .build();

        assertTrue(metadata.shouldTryInstall(new TestPlatform(tempDir, Side.CLIENT)));
        assertTrue(metadata.shouldTryInstall(new TestPlatform(tempDir, Side.SERVER)));
    }

    @Test
    void shouldTryInstallWhenPlatformSideIsNull() {
        RemoteModMetadata metadata = RemoteModMetadata.builder()
            .side(Side.CLIENT)
            .build();
        TestPlatform platform = new TestPlatform(tempDir, Side.CLIENT) {
            @Override
            public Side side() {
                return null;
            }
        };

        assertTrue(metadata.shouldTryInstall(platform));
    }
}
