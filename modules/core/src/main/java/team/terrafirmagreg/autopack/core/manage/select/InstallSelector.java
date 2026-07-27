package team.terrafirmagreg.autopack.core.manage.select;

import lombok.Getter;
import team.terrafirmagreg.autopack.core.configuration.InstallationPolicy;
import team.terrafirmagreg.autopack.core.configuration.RemoteMod;
import team.terrafirmagreg.autopack.core.manage.install.InstallableMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class InstallSelector {
    private final List<InstallableMod> alwaysInstall;
    private final List<SelectableInstallOption> singleOptions;
    private final Map<String, List<SelectableInstallOption>> groupOptions;
    private final Map<SelectableInstallOption, InstallableMod> optionsToMod;

    public InstallSelector() {
        this.alwaysInstall = new ArrayList<>();
        this.singleOptions = new ArrayList<>();
        this.groupOptions = new HashMap<>();
        this.optionsToMod = new HashMap<>();
    }

    public void accept(
        List<RemoteMod> excludedMods,
        List<InstallableMod> freshInstalls,
        List<InstallableMod> reInstall
    ) {
        List<String> ignoredGroups = new ArrayList<>();

        for (RemoteMod mod : excludedMods) {
            if (mod != null) {
                InstallationPolicy policy = mod.getInstallationPolicy();
                if (policy != null) {
                    String group = policy.optionalKey();
                    if (group != null && !group.equals("$")) {
                        ignoredGroups.add(group);
                    }
                }
            }
        }

        for (InstallableMod mod : reInstall) {
            if (mod != null) {
                RemoteMod remoteMod = mod.remoteMod();
                if (remoteMod != null) {
                    InstallationPolicy policy = remoteMod.getInstallationPolicy();
                    if (policy != null) {
                        String group = policy.optionalKey();
                        if (group != null && !group.equals("$")) {
                            ignoredGroups.add(group);
                        }
                    }
                    alwaysInstall.add(mod);
                }
            }
        }

        for (InstallableMod mod : freshInstalls) {
            if (mod != null) {
                RemoteMod remoteMod = mod.remoteMod();
                if (remoteMod != null) {
                    InstallationPolicy policy = remoteMod.getInstallationPolicy();
                    if (policy != null) {
                        String optionalKey = policy.optionalKey();
                        if (optionalKey == null) {
                            alwaysInstall.add(mod);
                        } else if (!ignoredGroups.contains(optionalKey)) {
                            SelectableInstallOption installOption = new SelectableInstallOption(
                                policy.isSelectedByDefault(),
                                policy.name() == null ? remoteMod.offlineName() : policy.name() + " - " + remoteMod.offlineName(),
                                policy.description()
                            );
                            if (optionalKey.equals("$")) {
                                singleOptions.add(installOption);
                            } else {
                                groupOptions.computeIfAbsent(optionalKey, k -> new ArrayList<>()).add(installOption);
                            }
                            optionsToMod.put(installOption, mod);
                        }
                    }
                }
            }
        }
    }

    public boolean hasSelectableOptions() {
        return !getSingleOptions().isEmpty() || !getGroupOptions().isEmpty();
    }

    public List<InstallableMod> computeModsToInstall() {
        List<InstallableMod> mods = new ArrayList<>(alwaysInstall);

        optionsToMod.forEach((option, mod) -> {
            if (option.isSelected()) {
                mods.add(mod);
            }
        });

        return mods;
    }

    public List<InstallableMod> computeDisabledMods() {
        List<InstallableMod> mods = new ArrayList<>();

        optionsToMod.forEach((option, mod) -> {
            if (!option.isSelected()) {
                mods.add(mod);
            }
        });

        return mods;
    }
}
