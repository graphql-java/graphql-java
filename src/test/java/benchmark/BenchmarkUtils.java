package benchmark;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

public class BenchmarkUtils {

    @SuppressWarnings("UnstableApiUsage")
    static String loadResource(String name) {
        return asRTE(() -> {
            URL resource = BenchmarkUtils.class.getClassLoader().getResource(name);
            if (resource == null) {
                throw new IllegalArgumentException("missing resource: " + name);
            }
            byte[] bytes;
            try (InputStream inputStream = resource.openStream()) {
                bytes = inputStream.readAllBytes();
            }
            return new String(bytes, Charset.defaultCharset());
        });
    }

    static <T> T asRTE(Callable<T> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
