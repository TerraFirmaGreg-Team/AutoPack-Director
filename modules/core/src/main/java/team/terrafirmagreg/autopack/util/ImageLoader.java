package team.terrafirmagreg.autopack.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

public class ImageLoader {
    private static final String FILE_PROTOCOL = "file://";
    private static final List<String> WEB_PROTOCOLS = Arrays.asList("https://", "http://");

    private static Image getScaled(BufferedImage image, int width, int height) {
        if (width <= 0 && height <= 0) {
            return image;
        }

        if (width <= 0) {
            width = image.getWidth();
        }

        if (height <= 0) {
            height = image.getHeight();
        }

        return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }

    public static Image getImage(String path, int width, int height) throws IOException, URISyntaxException {
        for (String protocol : WEB_PROTOCOLS) {
            if (path.startsWith(protocol)) {
                return fromWeb(path, width, height);
            }
        }
        if (path.startsWith(FILE_PROTOCOL)) {
            path = path.substring(7);
        }

        return fromFile(path, width, height);
    }

    private static Image fromFile(String path, int width, int height) throws IOException {
        return getScaled(ImageIO.read(new File(path)), width, height);
    }

    private static Image fromWeb(String path, int width, int height) throws IOException, URISyntaxException {
        try (WebGetResponse response = WebClient.get(new URI(path).toURL())) {
            return getScaled(ImageIO.read(response.getInputStream()), width, height);
        }
    }
}
