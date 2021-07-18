package benchmark;

import com.google.common.io.Files;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

public class BenchmarkUtils {

    static String loadResource(String name) {
        return asRTE(() -> {
            URL resource = BenchmarkUtils.class.getClassLoader().getResource(name);
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
