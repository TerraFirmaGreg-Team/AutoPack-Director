package net.jan.moddirector.core.manage.check;

import com.fasterxml.jackson.databind.JavaType;
import net.jan.moddirector.ModpackDirector;
import net.jan.moddirector.core.configuration.ConfigurationController;
import net.jan.moddirector.core.exception.ModDirectorException;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.util.WebClient;
import net.jan.moddirector.core.util.WebGetResponse;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class StopModReposts {
    private final List<StopModRepostsEntry> entries = new ArrayList<>();
    private final ModpackDirector director;

    public StopModReposts(ModpackDirector director) {
        this.director = director;
        try (WebGetResponse response =
                 WebClient.get(new URL("https://api.stopmodreposts.org/sites.json"))) {
            JavaType targetType = ConfigurationController.OBJECT_MAPPER.getTypeFactory().
                constructCollectionType(List.class, StopModRepostsEntry.class);

            entries.addAll(ConfigurationController.OBJECT_MAPPER.readValue(response.getInputStream(), targetType));
        } catch (Exception e) {
            director.logger().error("Failed to retrieve StopModReposts database", e);
        }
    }

    public void check(URL url) throws ModDirectorException {
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
                director.addError(new ModDirectorError(Level.SEVERE,
                    "Found URL " + url.toExternalForm() + " on domain " + entry.domain() + " flagged " +
                        "in the StopModReposts database! Please use legal download pages, " +
                        "ModDirector has aborted the launch."));
                throw new ModDirectorException("Found flagged URL " + url.toExternalForm() +
                    " in StopModReposts database");
            }
        }
    }
}
