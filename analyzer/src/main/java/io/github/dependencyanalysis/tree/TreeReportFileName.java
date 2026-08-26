package io.github.dependencyanalysis.tree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Stable Reactor report filename policy shared by tree operations. */
final class TreeReportFileName {

    /** SHA-256 prefix byte count. */
    private static final int HASH_BYTES = 6;

    private TreeReportFileName() {
    }

    /**
     * Returns a stable Reactor HTML filename.
     *
     * @param reactorKey normalized ReactorKey
     * @return slug plus six-byte SHA-256 prefix
     */
    static String of(final String reactorKey) {
        String slug = reactorKey.replace("pom.xml", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
        if (slug.isBlank()) {
            slug = "root";
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(reactorKey.getBytes(StandardCharsets.UTF_8));
            return slug + "-" + HexFormat.of()
                    .formatHex(digest, 0, HASH_BYTES) + ".html";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
