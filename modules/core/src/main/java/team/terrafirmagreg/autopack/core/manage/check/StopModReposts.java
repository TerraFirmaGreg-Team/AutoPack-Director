package team.terrafirmagreg.autopack.core.manage.check;

import com.fasterxml.jackson.databind.JavaType;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.core.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.core.exception.InstallException;
import team.terrafirmagreg.autopack.core.manage.InstallError;
import team.terrafirmagreg.autopack.core.util.WebClient;
import team.terrafirmagreg.autopack.core.util.WebGetResponse;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class StopModReposts {
    private final List<StopModRepostsEntry> entries = new ArrayList<>();
    private final Director director;

    public StopModReposts(Director director) {
        this(director, true);
    }

    public StopModReposts(Director director, boolean fetchEntries) {
        this.director = director;
        if (!fetchEntries) {
            return;
        }
        try (WebGetResponse response =
                 WebClient.get(new URL("https://api.stopmodreposts.org/sites.json"))) {
            JavaType targetType = ConfigurationController.OBJECT_MAPPER.getTypeFactory().
                constructCollectionType(List.class, StopModRepostsEntry.class);

            entries.addAll(ConfigurationController.OBJECT_MAPPER.readValue(response.getInputStream(), targetType));
        } catch (Exception e) {
            director.logger().error("Failed to retrieve StopModReposts database", e);
        }
    }

    public void check(URL url) throws InstallException {
        director.logger().debug("Checking {0} against StopModReposts database", url.toExternalForm());
        for (StopModRepostsEntry entry : entries) {
            if (url.toExternalForm().contains(entry.domain())) {
                director.getLogger().error("STOP! Download URL {0} is flagged in StopModReposts database, ABORTING!",
                    url.toExternalForm());
                director.getLogger().error("Domain {0} is flagged", entry.domain());
                director.getLogger().error("Reason: {0}", entry.reason());
                if (!entry.notes().isEmpty()) {
                    director.getLogger().error("Notes: {0}", entry.notes());
                }
                director.addError(new InstallError(Level.SEVERE,
                    "Found URL " + url.toExternalForm() + " on domain " + entry.domain() + " flagged " +
                        "in the StopModReposts database! Please use legal download pages. Launch aborted."));
                throw new InstallException("Found flagged URL " + url.toExternalForm() +
                    " in StopModReposts database");
            }
        }
    }
}
