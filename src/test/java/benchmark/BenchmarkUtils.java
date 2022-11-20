package benchmark;

import com.google.common.io.Files;
import graphql.Assert;

import java.io.File;
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
            return String.join("\n", Files.readLines(new File(resource.toURI()), Charset.defaultCharset()));
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
