package org.jpm;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Repository {
    private Path calculateRepositoryPath() {
        var path = Paths.get(System.getProperty("user.home")).toAbsolutePath();
        return path.resolve(".jpm");
    }

    private Path _repositoryPath;
    private Path _libPath;
    private Path _binPath;
    private Path _sharePath;

    public Repository() {
        _repositoryPath = calculateRepositoryPath();
        _libPath = _repositoryPath.resolve("lib");
        _binPath = _repositoryPath.resolve("bin");
        _sharePath = _repositoryPath.resolve("share");
        _repositoryPath.toFile().mkdirs();
        _libPath.toFile().mkdirs();
        _binPath.toFile().mkdirs();
        _sharePath.toFile().mkdirs();
    }

    public Path getRepositoryPath() {
        return _repositoryPath;
    }

    public Path getLibraryPath() {
        return _libPath;
    }

    public Path getBinaryPath() {
        return _binPath;
    }

    public Path getSharePath() {
        return _sharePath;
    }

    public Path getSharePath(Project project) {
        String projectName = project.getProjectName();
        Path result = getSharePath().resolve(projectName);
        result.toFile().mkdirs();
        return result;
    }
}
