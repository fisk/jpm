package org.jpm;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class DependencyDetector implements ModuleInfoParser.ModuleVisitor {
    private List<Dependency> _dependencies = new ArrayList<>();
    private Project _project;

    public void visitModule(int modifiers, String name) {}
    public void visitRequires(int modifiers, String module) {
        _dependencies.add(new Dependency(module));
    }
    public void visitExports(String packaze, List<String> toModules) {}
    public void visitOpens(String packaze, List<String> toModules) {}
    public void visitUses(String service) {}
    public void visitProvides(String service, List<String> providers) {}

    public DependencyDetector(Project project) {
        _project = project;
        findSourceModules();
        findBinaryModules(ModuleFinder.of(_project.getLibraryPath().resolve("main")), false);
        findBinaryModules(ModuleFinder.ofSystem(), true);
    }

    private void findSourceModules() {
        try {
            Files.walk(_project.getSourcePath())
                .filter(Files::isRegularFile)
                .forEach((file)->{
                        try {
                            String fileName = file.getFileName().toString();
                            if (fileName.equals("module-info.java")) {
                                ModuleInfoParser.parse(file, this);
                            }
                        } catch (IOException e) {
                        }
                    });
        } catch (IOException e) {
        }
    }

    private void findBinaryModules(ModuleFinder finder, boolean system) {
        var versionTable = new HashMap<String, String>();
        var pathTable = new HashMap<String, Path>();
        for (var module: finder.findAll()) {
            var descriptor = module.descriptor();
            var name = descriptor.name();
            var version = descriptor.rawVersion();
            if (version.isPresent()) {
                versionTable.put(name, version.get());
            }
            pathTable.put(name, Paths.get(module.location().get()));
        }

        for (var dependency: _dependencies) {
            String version = versionTable.get(dependency.getName());
            Path path = pathTable.get(dependency.getName());
            if (version != null) {
                dependency.setVersion(version);
            }
            if (path != null && !system) {
                dependency.setBinaryPath(path);
                if (version == null) {
                    var pattern = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+)\\.jar");
                    var matcher = pattern.matcher(path.getFileName().toString());
                    if (matcher.matches()) {
                        dependency.setVersion(matcher.group(1));
                    }
                }
            }
        }
    }

    public List<Dependency> getDependencies() {
        return _dependencies;
    }
}
