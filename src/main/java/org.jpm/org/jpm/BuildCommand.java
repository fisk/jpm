package org.jpm;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class BuildCommand {
    private Project _project;
    private DependencyDetector _deps;
    private String _mainClass;

    public BuildCommand() {
        _project = new Project();
        _deps = new DependencyDetector(_project);
    }

    public boolean getMainClass(Path path) {
        try {
            var in = new FileInputStream(path.toString());
            var reader = new ClassReader(in);
            var classNode = new ClassNode();
            reader.accept(classNode, 0);
            for (var method: classNode.methods) {
                if (!method.name.equals("main")) {
                    continue;
                }
                if (method.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                    continue;
                }
                return true;
            }
        } catch (Exception e) {
            System.out.println("Exception at " + path + ": " + e);
        }
        return false;
    }

    public String getMainClass(List<Path> classFiles) {
        var moduleClassPath = _project.getBuildPath().resolve(_project.getProjectName());
        var pool = Executors.newCachedThreadPool();
        for (var file: classFiles) {
            pool.submit(() -> {
                if (getMainClass(file)) {
                    _mainClass = moduleClassPath.relativize(file).toString().replaceAll("/", ".");
                    _mainClass = _mainClass.substring(0, _mainClass.length() - 6);
                }
          });
        }
        pool.shutdown();
        try {
          pool.awaitTermination(100, TimeUnit.DAYS);
        } catch (InterruptedException e) {
        }
        return _mainClass;
    }

    public String getMainClass() {
        var moduleClassPath = _project.getBuildPath().resolve(_project.getProjectName());
        List<Path> classFiles = new ArrayList<>();
        try {
            Files.walk(moduleClassPath).filter(Files::isRegularFile).forEach((file) -> {
                    if (!file.toString().endsWith(".class")) {
                        return;
                    }
                    classFiles.add(file);
                });
        } catch (IOException e) {}
        getMainClass(classFiles);
        return _mainClass;
    }

    public void downloadDependencies() {
        for (var dep: _deps.getDependencies()) {
            System.out.println("Dependency: " + dep.getName());
            if (dep.getBinaryPath() == null && !dep.isSystem()) {
                new GetCommand(dep.getName(), null, "main").run();
            }
        }
        _deps = new DependencyDetector(_project);
    }

    public void downloadTransitiveDependencies(Path jarFilePath) throws IOException {
        var jpmFile = JpmFile.fromJar(jarFilePath);
        if (jpmFile == null) {
            return;
        }
        for (var jpmDep: jpmFile.getMainDependencies()) {
            _deps = new DependencyDetector(_project);
            var dep = _deps.getDependency(jpmDep.getName());
            if (dep != null && (dep.isSystem() || dep.getBinaryPath() != null)) {
                continue;
            }
            new GetCommand(jpmDep.getName(), jpmDep.getVersion(), "transitive").run();
            var jpmPath = _project.getLibraryPath().resolve("transitive/" + jpmDep.getName() + "-" + jpmDep.getVersion() + ".jar");
            downloadTransitiveDependencies(jpmPath);
        }
    }

    public void downloadTransitiveDependencies() {
        try  {
            for (var dep: _deps.getDependencies()) {
                if (dep.getBinaryPath() != null && !dep.isSystem()) {
                    downloadTransitiveDependencies(dep.getBinaryPath());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed getting transitive dependencies: ", e);
        }
    }

    public void run() {
        String name = _project.getProjectName();
        String version = _project.getProjectVersion();
        downloadDependencies();
        downloadTransitiveDependencies();
        _deps = new DependencyDetector(_project);
        var mainJpm = _project.getBuildPath().resolve(_project.getProjectName());
        var jpmFile = _deps.getJpmFile();
        jpmFile.installDir(mainJpm);
        try {
            Files.write(_project.getResourcePath().resolve("main.jpm"), jpmFile.toString().getBytes());
        } catch (IOException e) {}
        Cmd.run("javac -d build --module-path lib/main:lib/transitive --module-source-path src/main/java src/main/java/**/*.java --module " + name + " -source 11");
        String mainClass = getMainClass();
        Cmd.run("jar --create --module-version=" + version + " --file=build/" + name + "-" + version + ".jar --main-class=" + mainClass + " -C build/" + name + " .");
        System.out.println(_deps.getDependencies());
    }
}
