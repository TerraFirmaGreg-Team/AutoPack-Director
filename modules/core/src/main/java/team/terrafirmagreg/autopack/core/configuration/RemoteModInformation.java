package team.terrafirmagreg.autopack.core.configuration;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record RemoteModInformation(String displayName, String targetFilename) {
}
