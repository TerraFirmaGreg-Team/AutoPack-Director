package team.terrafirmagreg.autopack.core.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import team.terrafirmagreg.autopack.util.PlatformDelegate;
import team.terrafirmagreg.autopack.util.Side;
import team.terrafirmagreg.autopack.core.util.HashResult;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

@Jacksonized
@Builder
public class RemoteModMetadata {
    @JsonProperty("hash")
    private final LinkedHashMap<String, String> hashes;

    @JsonProperty
    private final Side side;

    public HashResult checkHashes(Path file, PlatformDelegate platform) {
        if (hashes == null) {
            return HashResult.UNKNOWN;
        }

        byte[] data;

        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            platform.logger().warn("Failed to open {0} for hash calculation, assuming hash does not match",
                file.toString(), e);
            return HashResult.UNMATCHED;
        }

        StringBuilder hashBuilder = new StringBuilder();
        for (Map.Entry<String, String> hashEntry : hashes.entrySet()) {
            hashBuilder.setLength(0);

            try {
                MessageDigest digest = MessageDigest.getInstance(hashEntry.getKey());

                byte[] hash = digest.digest(data);
                hashBuilder.append(new BigInteger(1, hash).toString(16));
                while (hashBuilder.length() < 32) {
                    hashBuilder.insert(0, '0');
                }

                if (!hashBuilder.toString().equals(hashEntry.getValue())) {
                    return HashResult.UNMATCHED;
                } else {
                    return HashResult.MATCHED;
                }
            } catch (NoSuchAlgorithmException e) {
                platform.logger().warn("Hash algorithm {0} not supported by JVM", hashEntry.getKey());
            }
        }

        platform.logger().warn("All given hash algorithms are not supported by the JVM");
        return HashResult.UNKNOWN;
    }

    public boolean shouldTryInstall(PlatformDelegate platform) {
        Side currentSide = platform.side();
        return currentSide == null || side == Side.UNKNOWN || currentSide == side;
    }
}
