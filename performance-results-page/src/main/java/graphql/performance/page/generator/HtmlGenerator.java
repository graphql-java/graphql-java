package graphql.performance.page.generator;

import graphql.performance.page.model.BenchmarkSeries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class HtmlGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneOffset.UTC);

    private static final String[] COLORS = {
            "#4e79a7", "#f28e2b", "#e15759", "#76b7b2", "#59a14f",
            "#edc948", "#b07aa1", "#ff9da7", "#9c755f", "#bab0ac",
            "#af7aa1", "#86bcb6", "#d37295", "#8cd17d", "#b6992d"
    };

    public void generate(Map<String, List<BenchmarkSeries>> groupedSeries, Path outputDir, int totalFiles) throws IOException {
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve("index.html");

        StringBuilder html = new StringBuilder();
        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>graphql-java Performance Results</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
                <script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns@3"></script>
                <style>
                """);
        appendCss(html);
        html.append("""
                </style>
                </head>
                <body>
                """);

        appendHeader(html, groupedSeries, totalFiles);
        appendNav(html, groupedSeries);
        appendSections(html, groupedSeries);

        html.append("""
                </body>
                </html>
                """);

        Files.writeString(outputFile, html.toString());
    }

    private void appendCss(StringBuilder html) {
        html.append("""
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: #f5f5f5;
                    color: #333;
                    line-height: 1.6;
                }
                .header {
                    background: linear-gradient(135deg, #1a1a2e, #16213e);
                    color: white;
                    padding: 2rem;
                    text-align: center;
                }
                .header h1 { font-size: 1.8rem; margin-bottom: 0.5rem; }
                .header .meta { font-size: 0.9rem; opacity: 0.8; }
                .nav {
                    background: white;
                    border-bottom: 1px solid #ddd;
                    padding: 1rem 2rem;
                    position: sticky;
                    top: 0;
                    z-index: 100;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .nav-title { font-weight: 600; margin-bottom: 0.5rem; font-size: 0.85rem; color: #666; text-transform: uppercase; letter-spacing: 0.05em; }
                .nav-links { display: flex; flex-wrap: wrap; gap: 0.5rem; }
                .nav-links a {
                    text-decoration: none;
                    color: #4e79a7;
                    padding: 0.25rem 0.75rem;
                    border-radius: 4px;
                    font-size: 0.85rem;
                    background: #f0f4f8;
                    transition: background 0.2s;
                }
                .nav-links a:hover { background: #dce8f1; }
                .content { max-width: 1400px; margin: 0 auto; padding: 2rem; }
                .section { margin-bottom: 3rem; }
                .section h2 {
                    font-size: 1.4rem;
                    margin-bottom: 1rem;
                    padding-bottom: 0.5rem;
                    border-bottom: 2px solid #4e79a7;
                    scroll-margin-top: 4rem;
                }
                .chart-container {
                    background: white;
                    border-radius: 8px;
                    padding: 1.5rem;
                    margin-bottom: 1.5rem;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                }
                .chart-container h3 {
                    font-size: 1rem;
                    color: #555;
                    margin-bottom: 1rem;
                }
                .chart-wrapper { position: relative; height: 350px; }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    font-size: 0.85rem;
                    background: white;
                    border-radius: 8px;
                    overflow: hidden;
                    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                    margin-bottom: 1rem;
                }
                th, td { padding: 0.6rem 1rem; text-align: left; border-bottom: 1px solid #eee; }
                th { background: #f8f9fa; font-weight: 600; color: #555; }
                tr:hover { background: #f8f9fa; }
                .mode-badge {
                    display: inline-block;
                    padding: 0.15rem 0.5rem;
                    border-radius: 3px;
                    font-size: 0.75rem;
                    font-weight: 600;
                    text-transform: uppercase;
                }
                .mode-thrpt { background: #e8f5e9; color: #2e7d32; }
                .mode-avgt { background: #e3f2fd; color: #1565c0; }
                """);
    }

    private void appendHeader(StringBuilder html, Map<String, List<BenchmarkSeries>> groupedSeries, int totalFiles) {
        int totalSeries = groupedSeries.values().stream().mapToInt(List::size).sum();
        html.append("<div class=\"header\">\n");
        html.append("<h1>graphql-java Performance Results</h1>\n");
        html.append("<p class=\"meta\">");
        html.append(groupedSeries.size()).append(" benchmark classes &middot; ");
        html.append(totalSeries).append(" series &middot; ");
        html.append(totalFiles).append(" result files &middot; ");
        html.append("Generated ").append(DATE_FORMAT.format(Instant.now())).append(" UTC");
        html.append("</p>\n");
        html.append("</div>\n");
    }

    private void appendNav(StringBuilder html, Map<String, List<BenchmarkSeries>> groupedSeries) {
        html.append("<div class=\"nav\">\n");
        html.append("<div class=\"nav-title\">Benchmark Classes</div>\n");
        html.append("<div class=\"nav-links\">\n");
        for (String className : groupedSeries.keySet()) {
            html.append("<a href=\"#").append(toAnchor(className)).append("\">").append(className).append("</a>\n");
        }
        html.append("</div>\n</div>\n");
    }

    private void appendSections(StringBuilder html, Map<String, List<BenchmarkSeries>> groupedSeries) {
        html.append("<div class=\"content\">\n");

        int chartId = 0;
        for (Map.Entry<String, List<BenchmarkSeries>> entry : groupedSeries.entrySet()) {
            String className = entry.getKey();
            List<BenchmarkSeries> seriesList = entry.getValue();

            html.append("<div class=\"section\">\n");
            html.append("<h2 id=\"").append(toAnchor(className)).append("\">").append(className).append("</h2>\n");

            // Group series by mode for separate charts
            List<BenchmarkSeries> thrptSeries = seriesList.stream()
                    .filter(s -> "thrpt".equals(s.getMode()))
                    .toList();
            List<BenchmarkSeries> avgtSeries = seriesList.stream()
                    .filter(s -> "avgt".equals(s.getMode()))
                    .toList();

            if (!thrptSeries.isEmpty()) {
                String unit = thrptSeries.getFirst().getScoreUnit();
                appendChart(html, "Throughput (" + unit + ")", "chart_" + chartId++, thrptSeries);
            }
            if (!avgtSeries.isEmpty()) {
                String unit = avgtSeries.getFirst().getScoreUnit();
                appendChart(html, "Average Time (" + unit + ")", "chart_" + chartId++, avgtSeries);
            }

            // Latest results table
            appendLatestTable(html, seriesList);

            html.append("</div>\n");
        }

        html.append("</div>\n");
    }

    private void appendChart(StringBuilder html, String title, String canvasId, List<BenchmarkSeries> seriesList) {
        html.append("<div class=\"chart-container\">\n");
        html.append("<h3>").append(title).append("</h3>\n");
        html.append("<div class=\"chart-wrapper\">\n");
        html.append("<canvas id=\"").append(canvasId).append("\"></canvas>\n");
        html.append("</div>\n</div>\n");

        html.append("<script>\n");
        html.append("new Chart(document.getElementById('").append(canvasId).append("'), {\n");
        html.append("  type: 'line',\n");
        html.append("  data: {\n");
        html.append("    datasets: [\n");

        for (int i = 0; i < seriesList.size(); i++) {
            BenchmarkSeries series = seriesList.get(i);
            String color = COLORS[i % COLORS.length];

            html.append("      {\n");
            html.append("        label: ").append(jsString(series.getDisplayLabel())).append(",\n");
            html.append("        borderColor: '").append(color).append("',\n");
            html.append("        backgroundColor: '").append(color).append("33',\n");
            html.append("        borderWidth: 2,\n");
            html.append("        pointRadius: 3,\n");
            html.append("        pointHoverRadius: 6,\n");
            html.append("        tension: 0.1,\n");
            html.append("        data: [\n");

            for (BenchmarkSeries.DataPoint dp : series.getDataPoints()) {
                html.append("          { x: '").append(dp.timestamp().toString()).append("', y: ").append(formatScore(dp.score()));
                html.append(", commit: '").append(dp.commitHash(), 0, Math.min(8, dp.commitHash().length())).append("'");
                html.append(", error: ").append(formatScore(dp.scoreError()));
                html.append(" },\n");
            }

            html.append("        ]\n");
            html.append("      },\n");
        }

        html.append("    ]\n");
        html.append("  },\n");
        html.append("  options: {\n");
        html.append("    responsive: true,\n");
        html.append("    maintainAspectRatio: false,\n");
        html.append("    interaction: { mode: 'index', intersect: false },\n");
        html.append("    scales: {\n");
        html.append("      x: {\n");
        html.append("        type: 'time',\n");
        html.append("        time: { unit: 'week', displayFormats: { week: 'MMM d' } },\n");
        html.append("        title: { display: true, text: 'Date' }\n");
        html.append("      },\n");
        html.append("      y: {\n");
        html.append("        title: { display: true, text: '").append(title).append("' },\n");
        html.append("        beginAtZero: false\n");
        html.append("      }\n");
        html.append("    },\n");
        html.append("    plugins: {\n");
        html.append("      tooltip: {\n");
        html.append("        callbacks: {\n");
        html.append("          afterLabel: function(ctx) {\n");
        html.append("            var dp = ctx.raw;\n");
        html.append("            return 'Commit: ' + dp.commit + '\\n\\u00b1 ' + dp.error;\n");
        html.append("          }\n");
        html.append("        }\n");
        html.append("      },\n");
        html.append("      legend: { position: 'bottom' }\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("});\n");
        html.append("</script>\n");
    }

    private void appendLatestTable(StringBuilder html, List<BenchmarkSeries> seriesList) {
        html.append("<table>\n");
        html.append("<thead><tr>");
        html.append("<th>Benchmark</th><th>Mode</th><th>Params</th><th>Score</th><th>Error</th><th>Unit</th><th>Commit</th><th>Date</th>");
        html.append("</tr></thead>\n<tbody>\n");

        for (BenchmarkSeries series : seriesList) {
            if (series.getDataPoints().isEmpty()) {
                continue;
            }
            BenchmarkSeries.DataPoint latest = series.getDataPoints().getLast();
            String modeBadge = "<span class=\"mode-badge mode-" + series.getMode() + "\">" + series.getMode() + "</span>";

            html.append("<tr>");
            html.append("<td>").append(series.getMethodName()).append("</td>");
            html.append("<td>").append(modeBadge).append("</td>");
            html.append("<td>").append(series.getParamsString().isEmpty() ? "-" : series.getParamsString()).append("</td>");
            html.append("<td><strong>").append(formatScore(latest.score())).append("</strong></td>");
            html.append("<td>&plusmn; ").append(formatScore(latest.scoreError())).append("</td>");
            html.append("<td>").append(latest.scoreUnit()).append("</td>");
            html.append("<td><code>").append(latest.commitHash(), 0, Math.min(8, latest.commitHash().length())).append("</code></td>");
            html.append("<td>").append(DATE_FORMAT.format(latest.timestamp())).append("</td>");
            html.append("</tr>\n");
        }

        html.append("</tbody></table>\n");
    }

    private static String toAnchor(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String formatScore(double score) {
        if (score >= 1000) {
            return String.format("%.0f", score);
        } else if (score >= 1) {
            return String.format("%.4f", score);
        } else {
            return String.format("%.6f", score);
        }
    }
}
