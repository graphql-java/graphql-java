package graphql.performance.page;

import graphql.performance.page.analyzer.BenchmarkAnalyzer;
import graphql.performance.page.generator.HtmlGenerator;
import graphql.performance.page.model.BenchmarkSeries;
import graphql.performance.page.model.ResultFile;
import graphql.performance.page.parser.ResultFileParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: Main <inputDir> <outputDir>");
            System.exit(1);
        }

        File inputDir = new File(args[0]);
        Path outputDir = Path.of(args[1]);

        if (!inputDir.isDirectory()) {
            System.err.println("Input directory does not exist: " + inputDir);
            System.exit(1);
        }

        File[] jsonFiles = inputDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null || jsonFiles.length == 0) {
            System.err.println("No JSON files found in: " + inputDir);
            System.exit(1);
        }

        System.out.println("Found " + jsonFiles.length + " result files in " + inputDir);

        ResultFileParser parser = new ResultFileParser();
        List<ResultFile> resultFiles = new ArrayList<>();
        int errors = 0;

        for (File file : jsonFiles) {
            try {
                resultFiles.add(parser.parse(file));
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse " + file.getName() + ": " + e.getMessage());
                errors++;
            }
        }

        System.out.println("Parsed " + resultFiles.size() + " files successfully" + (errors > 0 ? " (" + errors + " errors)" : ""));

        BenchmarkAnalyzer analyzer = new BenchmarkAnalyzer();
        Map<String, List<BenchmarkSeries>> grouped = analyzer.analyze(resultFiles);

        System.out.println("Found " + grouped.size() + " benchmark classes:");
        for (Map.Entry<String, List<BenchmarkSeries>> entry : grouped.entrySet()) {
            int totalPoints = entry.getValue().stream()
                    .mapToInt(s -> s.getDataPoints().size())
                    .sum();
            System.out.println("  " + entry.getKey() + ": " + entry.getValue().size() + " series, " + totalPoints + " data points");
        }

        Instant earliest = resultFiles.stream().map(ResultFile::getTimestamp).min(Comparator.naturalOrder()).orElse(Instant.now());
        Instant latest = resultFiles.stream().map(ResultFile::getTimestamp).max(Comparator.naturalOrder()).orElse(Instant.now());

        HtmlGenerator generator = new HtmlGenerator();
        generator.generate(grouped, outputDir, jsonFiles.length, earliest, latest);

        System.out.println("Generated " + outputDir.resolve("index.html"));
    }
}
