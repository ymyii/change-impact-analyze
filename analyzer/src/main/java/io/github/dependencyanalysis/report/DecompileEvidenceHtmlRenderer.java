package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.DecompileComparisonSummary;

import java.util.List;
import java.util.stream.Collectors;

/** Renders source-free ChangePoint collection decompile evidence. */
final class DecompileEvidenceHtmlRenderer {

    private DecompileEvidenceHtmlRenderer() {
    }

    /**
     * Renders stable evidence or an empty fragment.
     *
     * @param comparisons unique stable comparisons
     * @return escaped HTML fragment
     */
    static String render(
            final List<DecompileComparisonSummary> comparisons) {
        if (comparisons.isEmpty()) {
            return "";
        }
        final StringBuilder body = new StringBuilder();
        body.append("<details><summary>Decompiled Java ChangePoint ")
                .append("collection evidence</summary><p class=\"warn\">")
                .append("Exact normalized Vineflower text equality ")
                .append("suppresses the method body ChangePoint and ")
                .append("short-circuits normalized SSA.</p>")
                .append("<div class=\"table-scroll\"><table>")
                .append("<thead><tr><th>Dependency</th><th>Method</th>")
                .append("<th>Class versions</th><th>Body hashes</th>")
                .append("<th>Status</th><th>Reason</th>")
                .append("<th>Suppression reasons</th><th>Elapsed</th>")
                .append("</tr></thead><tbody>");
        comparisons.forEach(value -> appendRow(body, value));
        return body.append("</tbody></table></div></details>").toString();
    }

    private static void appendRow(
            final StringBuilder body,
            final DecompileComparisonSummary comparison) {
        final String reasons = comparison.suppressionReasons().stream()
                .sorted().map(Enum::name).collect(Collectors.joining(", "));
        body.append("<tr><td><code>")
                .append(escape(comparison.oldArtifact() + " → "
                        + comparison.newArtifact()))
                .append("</code></td><td><code>")
                .append(escape(comparison.owner().replace('/', '.')
                        + "#" + comparison.name()
                        + comparison.descriptor()))
                .append("</code></td><td>")
                .append(comparison.oldMajorVersion()).append(" → ")
                .append(comparison.newMajorVersion())
                .append("</td><td><code>")
                .append(escape(comparison.oldHash() + " → "
                        + comparison.newHash()))
                .append("</code></td><td>")
                .append(escape(comparison.status().name()))
                .append("</td><td><code>")
                .append(escape(comparison.reason()))
                .append("</code></td><td><code>")
                .append(escape(reasons.isEmpty() ? "NONE" : reasons))
                .append("</code></td><td>")
                .append(comparison.elapsedMillis())
                .append(" ms</td></tr>");
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
