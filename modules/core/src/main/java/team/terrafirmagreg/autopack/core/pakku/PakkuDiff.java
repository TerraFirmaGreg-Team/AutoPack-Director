package team.terrafirmagreg.autopack.core.pakku;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class PakkuDiff {
    @Builder.Default
    private final List<PakkuConfigChange> configUpdates = Collections.emptyList();
    @Builder.Default
    private final List<PakkuMissingMod> missingMods = Collections.emptyList();

    public boolean hasConfigDrift() {
        return configUpdates != null && !configUpdates.isEmpty();
    }

    public boolean hasMissingMods() {
        return missingMods != null && !missingMods.isEmpty();
    }

    public boolean isEmpty() {
        return !hasConfigDrift() && !hasMissingMods();
    }
}
