package org.jpm;

import java.util.ArrayList;
import java.util.List;
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
        var jpmModulePattern = Pattern.compile(".*module: \\\"(.*)-(\\d+\\.\\d+\\.\\d+)\\\".*", Pattern.DOTALL);
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
}
