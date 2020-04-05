package org.jpm;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DependencyDetector implements ModuleInfoParser.ModuleVisitor {
    private List<Dependency> _dependencies = new ArrayList<>();
    private Map<String, Dependency> _dependenciesMap = new HashMap<>();
    private Project _project;

    public void visitModule(int modifiers, String name) {}
    public void visitRequires(int modifiers, String module) {
        var dep = new Dependency(module);
        _dependencies.add(dep);
        _dependenciesMap.put(module, dep);
    }
    public void visitExports(String packaze, List<String> toModules) {}
    public void visitOpens(String packaze, List<String> toModules) {}
    public void visitUses(String service) {}
    public void visitProvides(String service, List<String> providers) {}

    public DependencyDetector(Project project) {
        _project = project;
        findSourceModules();
        findBinaryModules(ModuleFinder.of(_project.getLibraryPath().resolve("main")), false);
        findBinaryModules(ModuleFinder.of(_project.getLibraryPath().resolve("transitive")), false);
        findBinaryModules(ModuleFinder.ofSystem(), true);
        findJpmModules();
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

    private void findJpmModules() {
        try {
            var jpmPath = _project.getResourcePath().resolve("main.jpm");
            String jpmString = new String(Files.readAllBytes(jpmPath));
            var jpmFile = JpmFile.fromFile(jpmString);
            for (var jpmDep: jpmFile.getMainDependencies()) {
                var dep = _dependenciesMap.get(jpmDep.getName());
                if (dep == null) {
                    dep = new Dependency(jpmDep.getName());
                    _dependenciesMap.put(jpmDep.getName(), dep);
                }
                dep.setVersion(jpmDep.getVersion());
            }
        } catch (Exception e) {}
    }

    private void findBinaryModules(ModuleFinder finder, boolean system) {
        for (var module: finder.findAll()) {
            var descriptor = module.descriptor();
            var name = descriptor.name();
            var dep = _dependenciesMap.get(name);
            if (dep == null) {
                dep = new Dependency(name);
                _dependenciesMap.put(name, dep);
            }
            var version = descriptor.rawVersion();
            var path = Paths.get(module.location().get());
            if (version.isPresent()) {
                dep.setVersion(version.get());
            } else {
                var pattern = Pattern.compile(".*-(\\d+\\.\\d+\\.\\d+)\\.jar");
                var matcher = pattern.matcher(path.getFileName().toString());
                if (matcher.matches()) {
                    dep.setVersion(matcher.group(1));
                } else {
                    throw new RuntimeException("Cant find version for " + name);
                }
            }
            dep.setBinaryPath(path);
            if (system) {
                dep.setIsSystem(system);
            }
        }
    }

    public List<Dependency> getDependencies() {
        return _dependencies;
    }

    public String getVersion(String name) {
        var dep = getDependency(name);
        if (dep == null) {
            throw new RuntimeException("No version for " + name);
        }
        return dep.getVersion();
    }

    public Dependency getDependency(String name) {
        return _dependenciesMap.get(name);
    }

    public JpmFile getJpmFile() {
        var jpmFile = JpmFile.createJpmFile(_project.getProjectName(), _project.getProjectVersion());
        for (var dep: _dependencies) {
            jpmFile.getMainDependencies().add(new JpmFile.JpmReference(dep.getName(), dep.getVersion()));
        }
        return jpmFile;
    }
}
