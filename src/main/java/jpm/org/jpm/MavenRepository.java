package org.jpm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MavenRepository {
    private static final String _downloadBaseURL = "https://repo1.maven.org/maven2/"; // "org/slf4j/slf4j-api/2.0.0-alpha1/slf4j-api-2.0.0-alpha1.jar"
    private static final String _searchBaseURL = "http://search.maven.org/solrsearch/select?";
    private static final Pattern _latestVersionPattern = Pattern.compile(".*\\\"latestVersion\\\"\\:\\\"([^\\\"]+)\\\".*", Pattern.MULTILINE);

    private Project _project;

    public MavenRepository(Project project) {
        _project = project;
    }

    public String searchVersion(String group, String artifact) {
        try {
            var args = "q=g:%22" + group + "%22+AND+a:%22" + artifact + "%22";
            var urlString = _searchBaseURL + args;
            var conn = new URL(urlString).openConnection();
            try (var reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                var text = reader.lines().collect(Collectors.joining("\n"));
                var matcher = _latestVersionPattern.matcher(text);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public void downloadDependency(Dependency dependency) {
        String[] components = dependency.getName().split("\\.");
        String artifact = components[components.length - 1];
        components[components.length - 1] = "";
        String group = String.join(".", components);
        group = group.substring(0, group.length() - 1);
        String version = searchVersion(group, artifact);
        if (version == null) {
            artifact = artifact.replaceAll("_", ".");
            version = searchVersion(group, artifact);
            if (version == null) {
                artifact = artifact.replaceAll("\\.", "-");
                version = searchVersion(group, artifact);
                if (version == null) {
                    return;
                }
            }
        }
        System.out.println("Downloading group: " + group + ", artifact: " + artifact + ", version: " + version);
        try {
            String fileName = dependency.getName().replaceAll("\\.", "-").replaceAll("_", "-") + "-" + version + ".jar";
            Path filePath = _project.getLibraryPath().resolve(fileName);
            String urlString = _downloadBaseURL + group.replaceAll("\\.", "/") + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
            InputStream in = new URL(urlString).openStream();
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
        }
    }
}
