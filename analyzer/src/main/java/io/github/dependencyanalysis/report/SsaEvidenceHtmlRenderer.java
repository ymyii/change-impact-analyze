package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;

import java.util.List;

// Wiki: wiki/features/report-generator.md - Overall Index
/** Renders the report-safe ChangePoint collection SSA evidence table. */
final class SsaEvidenceHtmlRenderer {

    private SsaEvidenceHtmlRenderer() {
    }

    /**
     * Renders stable evidence or an empty fragment.
     *
     * @param comparisons unique stable comparisons
     * @return escaped HTML fragment
     */
    static String render(final List<SsaComparisonEvidence> comparisons) {
        if (comparisons.isEmpty()) {
            return "";
        }
        final StringBuilder body = new StringBuilder();
        body.append("<details><summary>SSA ChangePoint collection evidence")
                .append("</summary><p class=\"warn\">A normalized SSA match ")
                .append("suppresses the method body ChangePoint after the ")
                .append("decompiled Java comparison misses. It is not ")
                .append("proof of source or complete runtime behavior ")
                .append("equivalence.</p><div class=\"table-scroll\"><table>")
                .append("<thead><tr><th>Dependency</th><th>Method</th>")
                .append("<th>Class versions</th><th>Body hashes</th>")
                .append("<th>Status</th><th>Reason</th><th>Elapsed</th>")
                .append("</tr></thead><tbody>");
        comparisons.forEach(value -> appendRow(body, value));
        return body.append("</tbody></table></div></details>").toString();
    }

    private static void appendRow(
            final StringBuilder body,
            final SsaComparisonEvidence comparison) {
        body.append("<tr><td><code>")
                .append(escape(comparison.getOldArtifact() + " → "
                        + comparison.getNewArtifact()))
                .append("</code></td><td><code>")
                .append(escape(comparison.getOwner().replace('/', '.')
                        + "#" + comparison.getName()
                        + comparison.getDescriptor()))
                .append("</code></td><td>")
                .append(comparison.getOldMajorVersion()).append(" → ")
                .append(comparison.getNewMajorVersion())
                .append("</td><td><code>")
                .append(escape(comparison.getOldHash() + " → "
                        + comparison.getNewHash()))
                .append("</code></td><td>")
                .append(escape(comparison.getStatus().name()))
                .append("</td><td><code>")
                .append(escape(comparison.getReason()))
                .append("</code></td><td>")
                .append(comparison.getElapsedMillis())
                .append(" ms</td></tr>");
    }

    /**
     * Returns the false-negative warning when SSA filtering is enabled.
     *
     * @param enabled whether SSA filtering is enabled
     * @return escaped warning fragment
     */
    static String warning(final boolean enabled) {
        return enabled ? "<p class=\"warn\">Normalized SSA matching is not "
                + "proof of source or complete runtime behavior equivalence. "
                + "It can suppress real changes and therefore cause false "
                + "negatives.</p>" : "";
    }

    private static String escape(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
