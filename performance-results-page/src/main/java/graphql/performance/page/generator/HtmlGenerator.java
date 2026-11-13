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

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private static final String[] COLORS = {
            "#4e79a7", "#f28e2b", "#e15759", "#76b7b2", "#59a14f",
            "#edc948", "#b07aa1", "#ff9da7", "#9c755f", "#bab0ac",
            "#af7aa1", "#86bcb6", "#d37295", "#8cd17d", "#b6992d"
    };

    public void generate(Map<String, List<BenchmarkSeries>> groupedSeries, Path outputDir, int totalFiles,
                         Instant earliestDate, Instant latestDate) throws IOException {
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
        appendFilterBar(html, earliestDate, latestDate);
        appendNav(html, groupedSeries);
        appendSections(html, groupedSeries);
        appendFilterScript(html);

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
                .filter-bar {
                    background: #fff;
                    border-bottom: 1px solid #ddd;
                    padding: 0.75rem 2rem;
                    display: flex;
                    align-items: center;
                    gap: 1rem;
                    flex-wrap: wrap;
                }
                .filter-bar label {
                    font-size: 0.85rem;
                    font-weight: 600;
                    color: #555;
                }
                .filter-bar input[type="date"] {
                    padding: 0.3rem 0.5rem;
                    border: 1px solid #ccc;
                    border-radius: 4px;
                    font-size: 0.85rem;
                    color: #333;
                }
                .filter-bar .presets {
                    display: flex;
                    gap: 0.35rem;
                    margin-left: 0.5rem;
                }
                .filter-bar .presets button {
                    padding: 0.3rem 0.7rem;
                    border: 1px solid #ccc;
                    border-radius: 4px;
                    background: #f0f4f8;
                    font-size: 0.8rem;
                    cursor: pointer;
                    color: #4e79a7;
                    font-weight: 500;
                    transition: background 0.2s, border-color 0.2s;
                }
                .filter-bar .presets button:hover { background: #dce8f1; border-color: #4e79a7; }
                .filter-bar .presets button.active { background: #4e79a7; color: white; border-color: #4e79a7; }
                .nav {
                    background: white;
                    border-bottom: 1px solid #ddd;
                    padding: 0.75rem 2rem;
                    position: sticky;
                    top: 0;
                    z-index: 100;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                }
                .nav-title { font-weight: 600; margin-bottom: 0.5rem; font-size: 0.85rem; color: #666; text-transform: uppercase; letter-spacing: 0.05em; }
                .nav-links { display: flex; flex-wrap: wrap; gap: 0.5rem; }
                .nav-links button {
                    border: 1px solid transparent;
                    color: #4e79a7;
                    padding: 0.25rem 0.75rem;
                    border-radius: 4px;
                    font-size: 0.85rem;
                    background: #f0f4f8;
                    cursor: pointer;
                    font-family: inherit;
                    transition: background 0.2s, border-color 0.2s;
                }
                .nav-links button:hover { background: #dce8f1; }
                .nav-links button.active { background: #4e79a7; color: white; border-color: #4e79a7; }
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
                .no-data { color: #999; font-style: italic; padding: 1rem; text-align: center; }
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

    private void appendFilterBar(StringBuilder html, Instant earliestDate, Instant latestDate) {
        String minDate = DATE_ONLY.format(earliestDate);
        String maxDate = DATE_ONLY.format(latestDate);
        html.append("<div class=\"filter-bar\">\n");
        html.append("<label>Date Range:</label>\n");
        html.append("<input type=\"date\" id=\"dateFrom\" value=\"").append(minDate)
                .append("\" min=\"").append(minDate).append("\" max=\"").append(maxDate).append("\">\n");
        html.append("<span>to</span>\n");
        html.append("<input type=\"date\" id=\"dateTo\" value=\"").append(maxDate)
                .append("\" min=\"").append(minDate).append("\" max=\"").append(maxDate).append("\">\n");
        html.append("<div class=\"presets\">\n");
        html.append("<button onclick=\"setPreset(30)\">30 days</button>\n");
        html.append("<button onclick=\"setPreset(90)\">90 days</button>\n");
        html.append("<button onclick=\"setPreset(180)\">6 months</button>\n");
        html.append("<button onclick=\"setPreset(0)\" class=\"active\">All time</button>\n");
        html.append("</div>\n");
        html.append("</div>\n");
    }

    private void appendNav(StringBuilder html, Map<String, List<BenchmarkSeries>> groupedSeries) {
        html.append("<div class=\"nav\">\n");
        html.append("<div class=\"nav-title\">Benchmark Classes</div>\n");
        html.append("<div class=\"nav-links\">\n");
        html.append("<button class=\"active\" onclick=\"filterClass(null, this)\">All</button>\n");
        for (String className : groupedSeries.keySet()) {
            html.append("<button onclick=\"filterClass('").append(toAnchor(className)).append("', this)\">")
                    .append(className).append("</button>\n");
        }
        html.append("</div>\n</div>\n");
    }

    private void appendSections(StringBuilder html, Map<String, List<BenchmarkSeries>> groupedSeries) {
        html.append("<div class=\"content\">\n");

        int chartId = 0;
        int tableId = 0;
        for (Map.Entry<String, List<BenchmarkSeries>> entry : groupedSeries.entrySet()) {
            String className = entry.getKey();
            List<BenchmarkSeries> seriesList = entry.getValue();

            html.append("<div class=\"section\" data-class=\"").append(toAnchor(className)).append("\">\n");
            html.append("<h2 id=\"").append(toAnchor(className)).append("\">").append(className).append("</h2>\n");

            String sectionId = toAnchor(className);

            // Each series (method+mode+params) gets its own chart
            for (BenchmarkSeries series : seriesList) {
                String mode = series.getMode();
                String unit = series.getScoreUnit();
                String modeLabel = "thrpt".equals(mode) ? "Throughput" : "Average Time";
                String title = series.getMethodName();
                if (!series.getParamsString().isEmpty()) {
                    title += " (" + series.getParamsString() + ")";
                }
                title += " - " + modeLabel + " (" + unit + ")";
                appendChart(html, title, "chart_" + chartId++, List.of(series), sectionId);
            }

            appendLatestTable(html, seriesList, "table_" + tableId++);

            html.append("</div>\n");
        }

        html.append("</div>\n");
    }

    private void appendChart(StringBuilder html, String title, String canvasId, List<BenchmarkSeries> seriesList, String sectionId) {
        html.append("<div class=\"chart-container\">\n");
        html.append("<h3>").append(title).append("</h3>\n");
        html.append("<div class=\"chart-wrapper\">\n");
        html.append("<canvas id=\"").append(canvasId).append("\"></canvas>\n");
        html.append("</div>\n</div>\n");

        html.append("<script>\n");
        html.append("(function() {\n");

        // Emit the full (unfiltered) dataset as a JS constant
        html.append("var fullData = [\n");
        for (int i = 0; i < seriesList.size(); i++) {
            BenchmarkSeries series = seriesList.get(i);
            html.append("  [\n");
            for (BenchmarkSeries.DataPoint dp : series.getDataPoints()) {
                html.append("    { x: '").append(dp.timestamp().toString()).append("', y: ").append(formatScore(dp.score()));
                html.append(", commit: '").append(dp.commitHash(), 0, Math.min(8, dp.commitHash().length())).append("'");
                html.append(", error: ").append(formatScore(dp.scoreError()));
                html.append(" },\n");
            }
            html.append("  ],\n");
        }
        html.append("];\n");

        // Create chart
        html.append("var chart = new Chart(document.getElementById('").append(canvasId).append("'), {\n");
        html.append("  type: 'line',\n");
        html.append("  data: { datasets: [\n");

        for (int i = 0; i < seriesList.size(); i++) {
            BenchmarkSeries series = seriesList.get(i);
            String color = COLORS[i % COLORS.length];
            html.append("    {\n");
            String label = seriesList.size() == 1 ? series.getMethodName()
                    : series.getParamsString().isEmpty() ? series.getMethodName() : series.getParamsString();
            html.append("      label: ").append(jsString(label)).append(",\n");
            html.append("      borderColor: '").append(color).append("',\n");
            html.append("      backgroundColor: '").append(color).append("33',\n");
            html.append("      borderWidth: 2, pointRadius: 3, pointHoverRadius: 6, tension: 0.1,\n");
            html.append("      data: fullData[").append(i).append("]\n");
            html.append("    },\n");
        }

        html.append("  ] },\n");
        html.append("  options: {\n");
        html.append("    responsive: true, maintainAspectRatio: false,\n");
        html.append("    interaction: { mode: 'index', intersect: false },\n");
        html.append("    scales: {\n");
        html.append("      x: { type: 'time', time: { unit: 'week', displayFormats: { week: 'MMM d' } }, title: { display: true, text: 'Date' } },\n");
        html.append("      y: { title: { display: true, text: ").append(jsString(title)).append(" }, beginAtZero: false }\n");
        html.append("    },\n");
        html.append("    plugins: {\n");
        html.append("      tooltip: { callbacks: { afterLabel: function(ctx) { var dp = ctx.raw; return 'Commit: ' + dp.commit + '\\n\\u00b1 ' + dp.error; } } },\n");
        html.append("      legend: { position: 'bottom' }\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("});\n");

        // Register chart for filtering
        html.append("window.perfCharts = window.perfCharts || [];\n");
        html.append("window.perfCharts.push({ chart: chart, fullData: fullData, sectionId: ").append(jsString(sectionId)).append(" });\n");

        html.append("})();\n");
        html.append("</script>\n");
    }

    private void appendLatestTable(StringBuilder html, List<BenchmarkSeries> seriesList, String tableId) {
        // Emit table data as JS for dynamic filtering
        html.append("<table id=\"").append(tableId).append("\">\n");
        html.append("<thead><tr>");
        html.append("<th>Benchmark</th><th>Mode</th><th>Params</th><th>Score</th><th>Error</th><th>Unit</th><th>Commit</th><th>Date</th>");
        html.append("</tr></thead>\n<tbody></tbody></table>\n");

        html.append("<script>\n");
        html.append("(function() {\n");
        html.append("var tableData = [\n");
        for (BenchmarkSeries series : seriesList) {
            if (series.getDataPoints().isEmpty()) continue;
            html.append("  { method: ").append(jsString(series.getMethodName()));
            html.append(", mode: ").append(jsString(series.getMode()));
            html.append(", params: ").append(jsString(series.getParamsString().isEmpty() ? "-" : series.getParamsString()));
            html.append(", unit: ").append(jsString(series.getScoreUnit()));
            html.append(", points: [\n");
            for (BenchmarkSeries.DataPoint dp : series.getDataPoints()) {
                html.append("      { t: '").append(dp.timestamp().toString()).append("'");
                html.append(", s: ").append(formatScore(dp.score()));
                html.append(", e: ").append(formatScore(dp.scoreError()));
                html.append(", c: '").append(dp.commitHash(), 0, Math.min(8, dp.commitHash().length())).append("'");
                html.append(" },\n");
            }
            html.append("    ]\n  },\n");
        }
        html.append("];\n");
        html.append("window.perfTables = window.perfTables || [];\n");
        html.append("window.perfTables.push({ id: '").append(tableId).append("', data: tableData });\n");
        html.append("})();\n");
        html.append("</script>\n");
    }

    private void appendFilterScript(StringBuilder html) {
        html.append("""
                <script>
                function applyDateFilter() {
                  var from = document.getElementById('dateFrom').value;
                  var to = document.getElementById('dateTo').value;
                  var fromDate = from ? new Date(from + 'T00:00:00Z') : null;
                  var toDate = to ? new Date(to + 'T23:59:59Z') : null;

                  // Update charts
                  (window.perfCharts || []).forEach(function(entry) {
                    entry.chart.data.datasets.forEach(function(ds, i) {
                      ds.data = entry.fullData[i].filter(function(dp) {
                        var d = new Date(dp.x);
                        if (fromDate && d < fromDate) return false;
                        if (toDate && d > toDate) return false;
                        return true;
                      });
                    });
                    entry.chart.update();
                  });

                  // Update tables
                  (window.perfTables || []).forEach(function(entry) {
                    var tbody = document.getElementById(entry.id).querySelector('tbody');
                    var rows = '';
                    entry.data.forEach(function(series) {
                      var filtered = series.points.filter(function(dp) {
                        var d = new Date(dp.t);
                        if (fromDate && d < fromDate) return false;
                        if (toDate && d > toDate) return false;
                        return true;
                      });
                      var latest = filtered.length > 0 ? filtered[filtered.length - 1] : null;
                      if (!latest) return;
                      var badge = '<span class="mode-badge mode-' + series.mode + '">' + series.mode + '</span>';
                      var dateStr = new Date(latest.t).toISOString().replace('T', ' ').substring(0, 16);
                      rows += '<tr>';
                      rows += '<td>' + series.method + '</td>';
                      rows += '<td>' + badge + '</td>';
                      rows += '<td>' + series.params + '</td>';
                      rows += '<td><strong>' + latest.s + '</strong></td>';
                      rows += '<td>&plusmn; ' + latest.e + '</td>';
                      rows += '<td>' + series.unit + '</td>';
                      rows += '<td><code>' + latest.c + '</code></td>';
                      rows += '<td>' + dateStr + '</td>';
                      rows += '</tr>';
                    });
                    tbody.innerHTML = rows || '<tr><td colspan="8" class="no-data">No data in selected range</td></tr>';
                  });
                }

                function setPreset(days) {
                  document.querySelectorAll('.presets button').forEach(function(b) { b.classList.remove('active'); });
                  event.target.classList.add('active');
                  if (days === 0) {
                    document.getElementById('dateFrom').value = document.getElementById('dateFrom').min;
                    document.getElementById('dateTo').value = document.getElementById('dateTo').max;
                  } else {
                    var to = new Date(document.getElementById('dateTo').max + 'T00:00:00Z');
                    var from = new Date(to);
                    from.setDate(from.getDate() - days);
                    document.getElementById('dateFrom').value = from.toISOString().substring(0, 10);
                    document.getElementById('dateTo').value = to.toISOString().substring(0, 10);
                  }
                  applyDateFilter();
                }

                document.getElementById('dateFrom').addEventListener('change', function() {
                  document.querySelectorAll('.presets button').forEach(function(b) { b.classList.remove('active'); });
                  applyDateFilter();
                });
                document.getElementById('dateTo').addEventListener('change', function() {
                  document.querySelectorAll('.presets button').forEach(function(b) { b.classList.remove('active'); });
                  applyDateFilter();
                });

                function filterClass(classId, btn) {
                  document.querySelectorAll('.nav-links button').forEach(function(b) { b.classList.remove('active'); });
                  btn.classList.add('active');
                  document.querySelectorAll('.section').forEach(function(sec) {
                    if (classId === null || sec.dataset.class === classId) {
                      sec.style.display = '';
                    } else {
                      sec.style.display = 'none';
                    }
                  });
                  // Resize visible charts so they render at correct dimensions
                  (window.perfCharts || []).forEach(function(entry) { entry.chart.resize(); });
                }

                // Initial table render
                applyDateFilter();
                </script>
                """);
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
