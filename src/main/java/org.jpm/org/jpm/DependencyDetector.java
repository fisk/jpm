package org.jpm;

import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
        findBinaryModules(ModuleFinder.of(_project.getLibraryPath()));
        findBinaryModules(ModuleFinder.ofSystem());
        for (var dependency: _dependencies) {
            if (dependency.getVersion() == null) {
                // Not downloaded yet; acquire it.
                new GetCommand(dependency.getName(), null);
            }
        }
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

    private void findBinaryModules(ModuleFinder finder) {
        var table = new HashMap<String, String>();
        for (var module: finder.findAll()) {
            var descriptor = module.descriptor();
            var name = descriptor.name();
            var version = descriptor.rawVersion();
            if (version.isPresent()) {
                table.put(name, version.get());
            }
        }

        for (var dependency: _dependencies) {
            String version = table.get(dependency.getName());
            if (version != null) {
                dependency.setVersion(version);
            }
        }
    }

    public List<Dependency> getDependencies() {
        return _dependencies;
    }
}
