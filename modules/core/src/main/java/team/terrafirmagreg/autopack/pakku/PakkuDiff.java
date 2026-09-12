package team.terrafirmagreg.autopack.pakku;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PakkuDiff {
    @Builder.Default
    private final List<PakkuConfigChange> configUpdates = Collections.emptyList();

    @Builder.Default
    private final List<PakkuMissingMod> missingMods = Collections.emptyList();

    public boolean hasConfigDrift() {
        return !configUpdates.isEmpty();
    }

    public boolean hasMissingMods() {
        return !missingMods.isEmpty();
    }

    public boolean isEmpty() {
        return !hasConfigDrift() && !hasMissingMods();
    }
}
