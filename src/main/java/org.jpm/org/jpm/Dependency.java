package org.jpm;

public class Dependency {
    private String _name;
    private String _version;

    public Dependency(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    public String getVersion() {
        return _version;
    }

    public void setVersion(String version) {
        _version = version;
    }

    @Override
    public String toString() {
        if (_version == null) {
            return _name;
        }
        return _name + ":" + _version;
    }
}
