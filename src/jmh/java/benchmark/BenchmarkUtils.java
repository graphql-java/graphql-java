package benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public static void runInToolingForSomeTimeThenExit(Runnable setup, Runnable r, Runnable tearDown) {
        int runForMillis = getRunForMillis();
        if (runForMillis <= 0) {
            System.out.print("'runForMillis' environment var is not set - continuing  \n");
            return;
        }
        System.out.printf("Running initial code in some tooling - runForMillis=%d  \n", runForMillis);
        System.out.print("Get your tooling in order and press enter...");
        readLine();
        System.out.print("Lets go...\n");
        setup.run();

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        long now, then = System.currentTimeMillis();
        do {
            now = System.currentTimeMillis();
            long msLeft = runForMillis - (now - then);
            System.out.printf("\t%s Running in loop... %s ms left\n", dtf.format(LocalDateTime.now()), msLeft);
            r.run();
            now = System.currentTimeMillis();
        } while ((now - then) < runForMillis);

        tearDown.run();

        System.out.printf("This ran for %d millis.  Exiting...\n", System.currentTimeMillis() - then);
        System.exit(0);
    }

    private static void readLine() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            br.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int getRunForMillis() {
        String runFor = System.getenv("runForMillis");
        try {
            return Integer.parseInt(runFor);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}
