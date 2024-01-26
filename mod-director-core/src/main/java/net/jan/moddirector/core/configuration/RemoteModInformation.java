package net.jan.moddirector.core.configuration;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record RemoteModInformation(String displayName, String targetFilename) {
}
