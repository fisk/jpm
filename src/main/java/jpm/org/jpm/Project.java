package org.jpm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Project {
    private Path calculateProjectPath() {
        var path = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        var root = path.getRoot();
        while (path != null && !root.equals(path)) {
            if (path.resolve(".git").toFile().exists()) {
                return path;
            } else {
                path = path.getParent();
            }
        }
        return null;
    }

    private Path _projectPath;
    private Path _sourcePath;
    private Path _buildPath;
    private Path _libPath;

    public Project() {
        _projectPath = calculateProjectPath();
        _sourcePath = _projectPath.resolve("src/main/java");
        _buildPath = _projectPath.resolve("build");
        _libPath = _projectPath.resolve("lib");
        _buildPath.toFile().mkdirs();
        _libPath.toFile().mkdirs();
    }

    public Path getProjectPath() {
        return _projectPath;
    }

    public Path getSourcePath() {
        return _sourcePath;
    }

    public Path getBuildPath() {
        return _buildPath;
    }

    public Path getLibraryPath() {
        return _libPath;
    }

    public Path getModulePath() {
        return getSourcePath().toFile().listFiles()[0].toPath();
    }

    public String getProjectName() {
        return getModulePath().getFileName().toString();
    }

    public String getProjectVersion() {
        var result = Cmd.run("git log --simplify-by-decoration --decorate --pretty=oneline \"HEAD\"");
        var regex = Pattern.compile("tag: (v\\d+\\.\\d+\\.\\d+)");
        var matcher = regex.matcher(result);
        if (!matcher.matches()) {
            return "1.0.0";
        } else {
            return matcher.group(1);
        }
    }
}

