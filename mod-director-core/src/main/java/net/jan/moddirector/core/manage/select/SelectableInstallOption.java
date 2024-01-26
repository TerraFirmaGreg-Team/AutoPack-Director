package net.jan.moddirector.core.manage.select;

import lombok.Getter;
import lombok.Setter;

@Getter
public class SelectableInstallOption {
    private final String description;
    private final String name;

    @Setter
    private volatile boolean selected;

    public SelectableInstallOption(boolean selectedByDefault, String name, String description) {
        this.selected = selectedByDefault;
        this.name = name;
        this.description = description;
    }

}
