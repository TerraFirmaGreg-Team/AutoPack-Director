package team.terrafirmagreg.autopack.manage.check;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import team.terrafirmagreg.autopack.Director;
import team.terrafirmagreg.autopack.configuration.ConfigurationController;
import team.terrafirmagreg.autopack.exception.InstallException;
import team.terrafirmagreg.autopack.locale.Messages;
import team.terrafirmagreg.autopack.manage.InstallError;
import team.terrafirmagreg.autopack.util.WebClient;
import team.terrafirmagreg.autopack.util.WebGetResponse;
import tools.jackson.databind.JavaType;

public class StopModReposts {
    private final List<StopModRepostsEntry> entries = new ArrayList<>();
    private final Director director;
    private boolean loaded;

    public StopModReposts(Director director) {
        this.director = director;
    }

    public void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (WebGetResponse response = WebClient.get(new URL("https://api.stopmodreposts.org/sites.json"))) {
            JavaType targetType = ConfigurationController.OBJECT_MAPPER
                    .getTypeFactory()
                    .constructCollectionType(List.class, StopModRepostsEntry.class);

            entries.addAll(ConfigurationController.OBJECT_MAPPER.readValue(response.getInputStream(), targetType));
        } catch (Exception e) {
            director.logger().error("Failed to retrieve StopModReposts database", e);
        }
    }

    public void check(URL url) throws InstallException {
        director.logger().debug("Checking {0} against StopModReposts database", url.toExternalForm());
        for (StopModRepostsEntry entry : entries) {
            if (url.toExternalForm().contains(entry.domain())) {
                director.getLogger()
                        .error(
                                "STOP! Download URL {0} is flagged in StopModReposts database, ABORTING!",
                                url.toExternalForm());
                director.getLogger().error("Domain {0} is flagged", entry.domain());
                director.getLogger().error("Reason: {0}", entry.reason());
                if (!entry.notes().isEmpty()) {
                    director.getLogger().error("Notes: {0}", entry.notes());
                }
                String message = Messages.get("autopack.error.stop_mod_reposts", url.toExternalForm(), entry.domain());
                director.addError(new InstallError(Level.SEVERE, message));
                throw new InstallException("Found flagged URL " + url.toExternalForm() + " in StopModReposts database");
            }
        }
    }
}
