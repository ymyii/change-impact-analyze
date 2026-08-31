package io.github.dependencyanalysis.reactor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inputs used to align bounded POM activation with Maven execution. */
public final class MavenActivationContext {

    /** Maven version output OS description. */
    private static final Pattern OS_LINE = Pattern.compile(
            "OS name: \\\"([^\\\"]*)\\\", version: \\\"([^\\\"]*)\\\", "
                    + "arch: \\\"([^\\\"]*)\\\"(?:, family: "
                    + "\\\"([^\\\"]*)\\\")?");

    /** Capture group containing Maven JVM OS architecture. */
    private static final int OS_ARCH_GROUP = 3;

    /** Attached short global-settings option length. */
    private static final int GLOBAL_SETTINGS_OPTION_LENGTH = 3;

    /** Attached short user-settings option length. */
    private static final int USER_SETTINGS_OPTION_LENGTH = 2;

    /** Validated Maven process tokens. */
    private final List<String> arguments;

    /** Java version used by the Maven subprocess. */
    private final String javaVersion;

    /** OS name reported by the Maven JVM. */
    private final String osName;

    /** OS architecture reported by the Maven JVM. */
    private final String osArch;

    /** OS version reported by the Maven JVM. */
    private final String osVersion;

    /** Profiles activated by Maven settings. */
    private final Set<String> settingsProfiles;

    private MavenActivationContext(
            final List<String> tokens,
            final String version,
            final String runtimeOsName,
            final String runtimeOsArch,
            final String runtimeOsVersion,
            final Set<String> activeProfiles) {
        arguments = List.copyOf(tokens);
        javaVersion = version == null ? "" : version;
        osName = runtimeOsName == null ? "" : runtimeOsName;
        osArch = runtimeOsArch == null ? "" : runtimeOsArch;
        osVersion = runtimeOsVersion == null ? "" : runtimeOsVersion;
        settingsProfiles = Set.copyOf(activeProfiles);
    }

    /**
     * Resolves activation inputs from Maven arguments and settings.
     *
     * @param arguments validated Maven arguments
     * @param javaVersion Maven JVM version
     * @return immutable activation inputs
     * @throws Exception when an explicitly selected settings file is invalid
     */
    public static MavenActivationContext resolve(
            final List<String> arguments,
            final String javaVersion) throws Exception {
        return resolve(arguments, javaVersion, null);
    }

    /**
     * Resolves activation inputs, including the runtime global settings.
     *
     * @param arguments validated Maven arguments
     * @param javaVersion Maven JVM version
     * @param mavenExecutable configured Maven executable, nullable
     * @return immutable activation inputs
     * @throws Exception when an explicitly selected settings file is invalid
     */
    public static MavenActivationContext resolve(
            final List<String> arguments,
            final String javaVersion,
            final Path mavenExecutable) throws Exception {
        return resolve(arguments, javaVersion,
                defaultGlobalSettings(mavenExecutable),
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System.getProperty("os.version", ""));
    }

    /**
     * Resolves activation inputs from one actual {@code mvn --version} probe.
     *
     * @param arguments validated Maven arguments
     * @param output Maven version command output
     * @param mavenExecutable configured Maven executable
     * @return immutable activation inputs
     * @throws Exception when settings cannot be parsed
     */
    public static MavenActivationContext resolveFromMavenOutput(
            final List<String> arguments,
            final String output,
            final Path mavenExecutable) throws Exception {
        return resolveFromMavenOutput(arguments, output, mavenExecutable,
                defaultGlobalSettings(mavenExecutable));
    }

    /**
     * Resolves activation inputs with the probed runtime global settings.
     *
     * @param arguments validated Maven arguments
     * @param output Maven version command output
     * @param mavenExecutable configured Maven executable
     * @param globalSettings probed runtime default settings, nullable
     * @return immutable activation inputs
     * @throws Exception when settings cannot be parsed
     */
    public static MavenActivationContext resolveFromMavenOutput(
            final List<String> arguments,
            final String output,
            final Path mavenExecutable,
            final Path globalSettings) throws Exception {
        String detectedJava = System.getProperty("java.version", "");
        String detectedName = System.getProperty("os.name", "");
        String detectedArch = System.getProperty("os.arch", "");
        String detectedVersion = System.getProperty("os.version", "");
        for (String line : output.lines().toList()) {
            final String value = line.trim();
            if (value.startsWith("Java version:")) {
                final String remainder = value.substring(
                        "Java version:".length()).trim();
                final int comma = remainder.indexOf(',');
                detectedJava = comma < 0 ? remainder
                        : remainder.substring(0, comma).trim();
            }
            final Matcher matcher = OS_LINE.matcher(value);
            if (matcher.find()) {
                detectedName = matcher.group(1);
                detectedVersion = matcher.group(2);
                detectedArch = matcher.group(OS_ARCH_GROUP);
            }
        }
        final Path runtimeSettings = globalSettings == null
                ? defaultGlobalSettings(mavenExecutable) : globalSettings;
        return resolve(arguments, detectedJava, runtimeSettings,
                detectedName, detectedArch, detectedVersion);
    }

    private static MavenActivationContext resolve(
            final List<String> arguments,
            final String javaVersion,
            final Path defaultGlobalSettings,
            final String osName,
            final String osArch,
            final String osVersion) throws Exception {
        final SettingsPaths paths = SettingsPaths.parse(arguments);
        final Set<String> profiles = new LinkedHashSet<>();
        if (paths.userSettings() != null) {
            profiles.addAll(readActiveProfiles(
                    paths.userSettings(), true));
        } else {
            final String userHome = System.getProperty("user.home", "");
            if (!userHome.isBlank()) {
                profiles.addAll(readActiveProfiles(Path.of(userHome)
                        .resolve(".m2/settings.xml"), false));
            }
        }
        if (paths.globalSettings() != null) {
            profiles.addAll(readActiveProfiles(
                    paths.globalSettings(), true));
        } else {
            if (defaultGlobalSettings != null) {
                profiles.addAll(readActiveProfiles(
                        defaultGlobalSettings, false));
            }
        }
        return new MavenActivationContext(
                arguments, javaVersion, osName, osArch, osVersion, profiles);
    }

    private static Path defaultGlobalSettings(final Path executable) {
        if (executable == null) {
            return null;
        }
        try {
            final Path real = executable.toRealPath();
            final Path bin = real.getParent();
            final Path home = bin == null ? null : bin.getParent();
            return home == null ? null : home.resolve("conf/settings.xml");
        } catch (IOException exception) {
            return null;
        }
    }

    /** @return validated Maven arguments */
    public List<String> getArguments() {
        return arguments;
    }

    /** @return Maven JVM version */
    public String getJavaVersion() {
        return javaVersion;
    }

    /** @return Maven JVM OS name */
    public String getOsName() {
        return osName;
    }

    /** @return Maven JVM OS architecture */
    public String getOsArch() {
        return osArch;
    }

    /** @return Maven JVM OS version */
    public String getOsVersion() {
        return osVersion;
    }

    /** @return settings-activated profile ids */
    public Set<String> getSettingsProfiles() {
        return settingsProfiles;
    }

    private static Set<String> readActiveProfiles(
            final Path settings,
            final boolean required) throws Exception {
        if (!Files.isRegularFile(settings)) {
            if (required) {
                throw new IllegalArgumentException(
                        "Maven settings file is unavailable: " + settings);
            }
            return Set.of();
        }
        final DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/"
                + "disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/"
                + "external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/"
                + "external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        final Document document = factory.newDocumentBuilder()
                .parse(settings.toFile());
        final Set<String> result = new LinkedHashSet<>();
        final NodeList nodes = document.getElementsByTagNameNS(
                "*", "activeProfile");
        if (nodes.getLength() == 0) {
            collectUnqualified(document, result);
        } else {
            for (int index = 0; index < nodes.getLength(); index++) {
                addProfile(nodes.item(index), result);
            }
        }
        return result;
    }

    private static void collectUnqualified(
            final Document document,
            final Set<String> result) {
        final NodeList nodes = document.getElementsByTagName("activeProfile");
        for (int index = 0; index < nodes.getLength(); index++) {
            addProfile(nodes.item(index), result);
        }
    }

    private static void addProfile(
            final Node node,
            final Set<String> result) {
        if (!(node instanceof Element)) {
            return;
        }
        final String value = node.getTextContent().trim();
        if (!value.isBlank()) {
            result.add(value);
        }
    }

    /**
     * Explicit user/global Maven settings paths.
     *
     * @param userSettings user settings path
     * @param globalSettings global settings path
     */
    private record SettingsPaths(Path userSettings, Path globalSettings) {

        /**
         * Parses supported settings option shapes.
         *
         * @param arguments validated Maven arguments
         * @return selected settings paths
         */
        static SettingsPaths parse(final List<String> arguments) {
            Path user = null;
            Path global = null;
            for (int index = 0; index < arguments.size(); index++) {
                final String argument = arguments.get(index);
                final String lower = argument.toLowerCase(
                        java.util.Locale.ROOT);
                if (lower.equals("-s") || lower.equals("--settings")) {
                    user = Path.of(arguments.get(++index));
                } else if (lower.equals("-gs")
                        || lower.equals("--global-settings")) {
                    global = Path.of(arguments.get(++index));
                } else if (lower.startsWith("--settings=")) {
                    user = inlinePath(argument);
                } else if (lower.startsWith("--global-settings=")) {
                    global = inlinePath(argument);
                } else if (lower.startsWith("-gs")
                        && argument.length()
                        > GLOBAL_SETTINGS_OPTION_LENGTH) {
                    global = attachedPath(argument,
                            GLOBAL_SETTINGS_OPTION_LENGTH);
                } else if (lower.startsWith("-s")
                        && argument.length()
                        > USER_SETTINGS_OPTION_LENGTH) {
                    user = attachedPath(argument,
                            USER_SETTINGS_OPTION_LENGTH);
                }
            }
            return new SettingsPaths(user, global);
        }

        private static Path inlinePath(final String argument) {
            return Path.of(argument.substring(argument.indexOf('=') + 1));
        }

        private static Path attachedPath(
                final String argument,
                final int optionLength) {
            String value = argument.substring(optionLength);
            if (value.startsWith("=")) {
                value = value.substring(1);
            }
            return Path.of(value);
        }
    }
}
