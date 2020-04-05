package org.jpm;

import java.nio.file.Path;
import java.nio.file.Paths;
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
    private Path _resourcePath;
    private Path _buildPath;
    private Path _libPath;

    public Project() {
        _projectPath = calculateProjectPath();
        _sourcePath = _projectPath.resolve("src/main/java");
        _resourcePath = _projectPath.resolve("src/main/resources");
        _buildPath = _projectPath.resolve("build");
        _libPath = _projectPath.resolve("lib");
        _buildPath.toFile().mkdirs();
        _libPath.resolve("main").toFile().mkdirs();
        _libPath.resolve("transitive").toFile().mkdirs();
        _resourcePath.toFile().mkdirs();
    }

    public Path getProjectPath() {
        return _projectPath;
    }

    public Path getSourcePath() {
        return _sourcePath;
    }

    public Path getResourcePath() {
      return _resourcePath;
    }

    public Path getBuildPath() {
        return _buildPath;
    }

    public Path getLibraryPath() {
        return _libPath;
    }

    public Path getModulePath() {
        for (var file: getSourcePath().toFile().listFiles()) {
            if (!file.getName().startsWith(".")) {
                return file.toPath();
            }
        }
        return null;
    }

    public String getProjectName() {
        return getModulePath().getFileName().toString();
    }

    private String _version;

    public String getProjectVersion() {
        if (_version != null) {
            return _version;
        }
        var regex = Pattern.compile(".*tag\\: v(\\d+\\.\\d+\\.\\d+).*");
        var result = Cmd.run("git log --simplify-by-decoration --decorate --pretty=oneline \"HEAD\"");
        var lines = result.split("\n");
        for (var line: lines) {
          var matcher = regex.matcher(line);
          if (matcher.matches()) {
              _version = matcher.group(1);
              return _version;
          }
        }
        _version = "1.0.0";
        return _version;
    }

    public String getProjectJarName() {
        return getProjectName() + "-" + getProjectVersion() + ".jar";
    }
}

