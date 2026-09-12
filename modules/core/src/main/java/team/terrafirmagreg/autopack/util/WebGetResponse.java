package team.terrafirmagreg.autopack.util;

import java.io.IOException;
import java.io.InputStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WebGetResponse implements AutoCloseable {
    private final InputStream inputStream;
    private final long streamSize;

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
