package org.jpm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

public class JpmFile {
    public static class JpmReference {
        private String _name;
        private String _version;

        public JpmReference(String name, String version) {
            _name = name;
            _version = version;
        }

        public String getName() {
            return _name;
        }

        public String getVersion() {
            return _version;
        }
    }
    
    public int hashCode() {
        return _main._name.hashCode() + _main._version.hashCode();
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof JpmFile)) {
            return false;
        }
        JpmFile other = (JpmFile)o;
        return _main._name.equals(other._main._name) && _main._version.equals(other._main._version);
    }

    private JpmReference _main;
    private List<JpmReference> _mainDependencies = new ArrayList<>();

    public List<JpmReference> getMainDependencies() {
        return _mainDependencies;
    }

    public JpmReference getMain() {
        return _main;
    }

    public static JpmFile fromFile(String jpmString) {
        JpmFile file = new JpmFile();
        var jpmModulePattern = Pattern.compile(".*module: \\\"([^\\\"]*)-(\\d+\\.\\d+\\.\\d+)\\\".*", Pattern.DOTALL);
        var jpmDepPattern = Pattern.compile(".*dependencies: \\[(.*)\\].*", Pattern.DOTALL);

        var jpmModuleMatcher = jpmModulePattern.matcher(jpmString);
        if (!jpmModuleMatcher.matches()) {
            return null;
        }

        file._main = new JpmReference(jpmModuleMatcher.group(1), jpmModuleMatcher.group(2));

        var jpmDepMatcher = jpmDepPattern.matcher(jpmString);
        if (!jpmDepMatcher.matches()) {
            return null;
        }

        var jpmDepsStr = jpmDepMatcher.group(1);
        jpmDepsStr = jpmDepsStr.replaceAll("\n", "");
        jpmDepsStr = jpmDepsStr.replaceAll(" ", "");

        var jpmDeps = jpmDepsStr.split(",");

        for (var jpmDep : jpmDeps) {
            if (!jpmDep.contains("-")) {
                continue;
            }
            jpmDep = jpmDep.substring(1, jpmDep.length() - 1);
            var jpmDepComponents = jpmDep.split("-");
            var jpmName = jpmDepComponents[0];
            var jpmVersion = jpmDepComponents[1];
            file._mainDependencies.add(new JpmReference(jpmName, jpmVersion));
        }
        return file;
    }

    public static JpmFile fromJar(Path jarFilePath) {
        try {
            var jarFile = new JarFile(jarFilePath.toFile());
            var jpmEntry = jarFile.getEntry("META-INF/jpm/main.jpm");
            if (jpmEntry == null) {
                var pattern = Pattern.compile("(.*)-(\\d+\\.\\d+\\.\\d+)\\.jar");
                var matcher = pattern.matcher(jarFilePath.getFileName().toString());
                if (matcher.matches()) {
                    var name = matcher.group(1);
                    var version = matcher.group(2);
                    var jpmFile = createJpmFile(name, version);
                    jpmFile.installJar(jarFilePath);
                    return jpmFile;
                }
                throw new RuntimeException("Could not install JPM file into " + jarFilePath);
            }
            var jpmStream = jarFile.getInputStream(jpmEntry);
            var jpmStr = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(jpmStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
                int c = 0;
                while ((c = reader.read()) != -1) {
                    jpmStr.append((char) c);
                }
            }
            return fromFile(jpmStr.toString());
        } catch (Exception e) {
            throw new RuntimeException("Could not install JPM file into " + jarFilePath + ": " + e);
        }
    }

    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("{\n");
        str.append("    module: \"" + _main.getName() + "-" + _main.getVersion() + "\"\n");
        str.append("    dependencies: [");
        int i = 0;
        for (var dependency: _mainDependencies) {
            String dependencyName = dependency.getName();
            String dependencyVersion = dependency.getVersion();
            if (dependencyVersion == null) {
                throw new RuntimeException("Dependency version can't be null for " + dependencyName);
            }
            if (i++ != 0) {
                str.append(",");
            }
            str.append("\n");
            str.append("        \"" + dependencyName + "-" + dependencyVersion + "\"");
        }
        str.append("\n    ]\n");
        str.append("}\n");
        return str.toString();
    }

    public static JpmFile createJpmFile(String name, String version) {
        var jpmFile = new JpmFile();
        jpmFile._main = new JpmReference(name, version);
        return jpmFile;
    }

    public void installJar(Path jarPath) {
        installDir(jarPath.getParent());
        Cmd.run("jar --update --file=" + jarPath.toString() + " -C " + jarPath.getParent() + " " + "META-INF/jpm/main.jpm");
        jarPath.getParent().resolve("META-INF").toFile().delete();
    }

    public void installDir(Path dir) {
        Path jpmPath = dir.resolve("META-INF").resolve("jpm");
        Path jpmFilePath = jpmPath.resolve("main.jpm");
        String jpmFileString = toString();
        jpmPath.toFile().mkdirs();
        try {
            Files.write(jpmFilePath, jpmFileString.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not create JPM file: ", e);
        }
    }
}
