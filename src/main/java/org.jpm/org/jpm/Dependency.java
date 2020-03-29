package org.jpm;

import java.nio.file.Path;

public class Dependency {
    private String _name;
    private String _version;
    private Path _binaryPath;
    private boolean _isSystem;
    private boolean _isDirect;

    public Dependency(String name, boolean isDirect) {
        _name = name;
        _isDirect = isDirect;
    }

    public String getName() {
        return _name;
    }

    public String getVersion() {
        return _version;
    }

    public boolean isSystem() {
        return _isSystem;
    }

    public void setIsSystem(boolean isSystem) {
        _isSystem = isSystem;
    }

    public void setVersion(String version) {
        _version = version;
    }

    public Path getBinaryPath() {
        return _binaryPath;
    }

    public void setBinaryPath(Path path) {
        _binaryPath = path;
    }

    @Override
    public String toString() {
        if (_version == null) {
            return _name;
        }
        return _name + ":" + _version;
    }
}
