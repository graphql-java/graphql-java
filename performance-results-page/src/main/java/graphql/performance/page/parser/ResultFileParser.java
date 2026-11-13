package graphql.performance.page.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.performance.page.model.BenchmarkResult;
import graphql.performance.page.model.ResultFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Arrays;
import java.util.List;

public class ResultFileParser {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
            .toFormatter()
            .withZone(java.time.ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses a JMH result JSON file. Extracts metadata from the filename and benchmark results from the JSON content.
     * <p>
     * Filename format: {timestamp}-{commitHash}-{jdkVersion}.json
     * e.g. 2025-02-28T05-10-58Z-77adc96ca0deeb4098d1ff1450312cf30d18e6a4-jdk17.json
     */
    public ResultFile parse(File file) throws IOException {
        String filename = file.getName().replace(".json", "");

        // Parse the filename: timestamp is the first 20 chars, then commit hash, then jdk version
        // Format: 2025-02-28T05-10-58Z-{commitHash}-{jdkVersion}
        String timestampStr = filename.substring(0, 20); // "2025-02-28T05-10-58Z"
        String remainder = filename.substring(21); // "{commitHash}-{jdkVersion}"

        // The remainder is commitHash-jdkVersion. The jdk version is at the end after the last '-'
        int lastDash = remainder.lastIndexOf('-');
        String commitHash = remainder.substring(0, lastDash);
        String jdkVersion = remainder.substring(lastDash + 1);

        Instant timestamp = TIMESTAMP_FORMAT.parse(timestampStr, Instant::from);

        List<BenchmarkResult> results = Arrays.asList(
                objectMapper.readValue(file, BenchmarkResult[].class)
        );

        return new ResultFile(timestamp, commitHash, jdkVersion, results);
    }
}
