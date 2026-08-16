package io.github.dependencyanalysis.cli;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies final packaged-JAR HTML Report structure and local resources. */
final class HtmlReportUsabilityVerifier {

    /** Local href/src attribute matcher. */
    private static final Pattern RESOURCE = Pattern.compile(
            "(?is)\\b(?:href|src)\\s*=\\s*([\"'])(.*?)\\1");

    /** Affected Paths manifest local shard matcher. */
    private static final Pattern SHARD_RESOURCE = Pattern.compile(
            "\\\"file\\\":\\\"([^\\\"]+\\.js)\\\"");

    /** Canonical Schema 3 shard header matcher. */
    private static final Pattern SHARD_HEADER = Pattern.compile(
            "^window\\.__CIA_AFFECTED_PATH_SHARD__\\(\\{"
            + "\\\"schemaVersion\\\":3,\\\"kind\\\":\\\"([a-z]+)\\\","
            + "\\\"shardId\\\":([0-9]+),\\\"records\\\":\\[");

    /** Non-empty title matcher. */
    private static final Pattern TITLE = Pattern.compile(
            "(?is)<title(?:\\s[^>]*)?>(.*?)</title>");

    /** Paired marker left behind by an unexpanded mustache-style template. */
    private static final Pattern UNEXPANDED_TEMPLATE = Pattern.compile(
            "(?s)\\{\\{[^{}]*}}");

    private HtmlReportUsabilityVerifier() {
    }

    /**
     * Verifies an Impact entry file and every reachable local HTML page.
     *
     * @param entry final Impact Report entry file
     * @throws IOException when a Report resource cannot be read
     */
    static void verifyImpact(final Path entry) throws IOException {
        final Path normalized = entry.toAbsolutePath().normalize();
        verify(normalized.getParent(), normalized, false);
    }

    /**
     * Verifies a Tree index and requires a reachable reactor page.
     *
     * @param output final Tree Report directory
     * @throws IOException when a Report resource cannot be read
     */
    static void verifyTree(final Path output) throws IOException {
        final Path root = output.toAbsolutePath().normalize();
        verify(root, root.resolve("index.html"), true);
    }

    private static void verify(
            final Path root,
            final Path entry,
            final boolean requireLinkedPage) throws IOException {
        assertThat(root).isDirectory();
        assertThat(entry).isRegularFile().isReadable();
        final Queue<Path> pending = new ArrayDeque<>();
        final Set<Path> visited = new HashSet<>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            final Path page = pending.remove().toAbsolutePath().normalize();
            if (!visited.add(page)) {
                continue;
            }
            assertThat(page).startsWith(root);
            assertThat(page).isRegularFile().isReadable();
            final String html = Files.readString(
                    page, StandardCharsets.UTF_8);
            verifyDocument(page, html, !requireLinkedPage);
            final Matcher resources = RESOURCE.matcher(html);
            while (resources.find()) {
                final String reference = resources.group(2).trim();
                if (ignored(reference)) {
                    continue;
                }
                final Path target = resolve(page, reference);
                assertThat(target).as("local resource from %s: %s",
                        page, reference).startsWith(root);
                assertThat(target).as("local resource from %s: %s",
                        page, reference).isRegularFile().isReadable();
                if (target.getFileName().toString()
                        .toLowerCase(Locale.ROOT).endsWith(".html")) {
                    pending.add(target);
                }
            }
            final Matcher shards = SHARD_RESOURCE.matcher(html);
            while (shards.find()) {
                final String reference = shards.group(1);
                final Path target = resolve(page, reference);
                assertThat(target).as("Affected Paths shard from %s: %s",
                        page, reference).startsWith(root);
                assertThat(target).as("Affected Paths shard from %s: %s",
                        page, reference).isRegularFile().isReadable();
                verifyShard(target, Files.readString(
                        target, StandardCharsets.UTF_8));
            }
        }
        if (requireLinkedPage) {
            assertThat(visited).as("reachable Tree HTML pages").hasSizeGreaterThan(1);
        }
    }

    private static void verifyDocument(
            final Path page,
            final String html,
            final boolean impactReport) {
        final String normalized = html.toLowerCase(Locale.ROOT);
        assertThat(html).as(page.toString()).isNotBlank();
        assertThat(normalized).as(page.toString())
                .contains("<!doctype html")
                .contains("<html")
                .contains("<head")
                .contains("<body")
                .contains("</body>")
                .contains("</html>");
        final Matcher title = TITLE.matcher(html);
        assertThat(title.find()).as("title in %s", page).isTrue();
        assertThat(title.group(1).trim()).as("title in %s", page).isNotBlank();
        assertThat(UNEXPANDED_TEMPLATE.matcher(html).find())
                .as("unexpanded template marker in %s", page)
                .isFalse();
        if (impactReport) {
            assertThat(html).as("HTML Diagnostics removed from %s", page)
                    .doesNotContain("id=\"diagnostics\"");
        }
        if (html.contains("id=\"affected-path-manifest\"")) {
            assertThat(html).as("Affected Paths contract in %s", page)
                    .contains("id=\"path-table\"")
                    .contains("\"schemaVersion\":3")
                    .contains("\"rowRanges\"")
                    .contains("\"shards\"")
                    .contains("__CIA_AFFECTED_PATH_SHARD__")
                    .contains("position:sticky")
                    .contains(".table-scroll")
                    .contains(".badge")
                    .contains(":focus-visible")
                    .contains("<noscript>");
        }
        if (html.contains("id=\"changed-member-data\"")) {
            assertThat(html).as("Changed member table contract in %s", page)
                    .contains("id=\"changed-member-table\"")
                    .contains("\"memberMetrics\"")
                    .contains("id=\"member-page-size\"")
                    .contains("aria-live=\"polite\"");
        }
    }

    private static void verifyShard(
            final Path file,
            final String content) {
        final Matcher header = SHARD_HEADER.matcher(content);
        assertThat(header.find()).as("Schema 3 header in %s", file)
                .isTrue();
        final String expectedName = header.group(1) + "-" + String.format(
                Locale.ROOT, "%05d", Integer.parseInt(header.group(2)))
                + ".js";
        assertThat(file.getFileName().toString()).as(file.toString())
                .isEqualTo(expectedName);
        assertThat(content).as(file.toString())
                .startsWith("window.__CIA_AFFECTED_PATH_SHARD__(")
                .contains("\"records\":[")
                .endsWith("]});\n");
    }

    private static boolean ignored(final String reference) {
        if (reference.isBlank() || reference.startsWith("#")) {
            return true;
        }
        final String lower = reference.toLowerCase(Locale.ROOT);
        return lower.startsWith("http:") || lower.startsWith("https:")
                || lower.startsWith("mailto:")
                || lower.startsWith("data:")
                || lower.startsWith("javascript:");
    }

    private static Path resolve(
            final Path page,
            final String reference) {
        final int fragment = reference.indexOf('#');
        final String withoutFragment = fragment < 0
                ? reference : reference.substring(0, fragment);
        final int query = withoutFragment.indexOf('?');
        final String path = query < 0
                ? withoutFragment : withoutFragment.substring(0, query);
        return page.getParent().resolve(URLDecoder.decode(
                path, StandardCharsets.UTF_8)).toAbsolutePath().normalize();
    }
}
