package org.jpm;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    public void run() {
        String name = _project.getProjectName();
        String version = _project.getProjectVersion();
        var jpmDeps = new ArrayList<JpmFile>();
        var jpm = _deps.getJpmFile();
        try (var db = JpmDatabase.remoteDatabase()) {
            for (var jpmRef: jpm.getMainDependencies()) {
                var dep = _deps.getDependency(jpmRef.getName());
                if (dep != null && dep.isSystem()) {
                    continue;
                }
                JpmFile jpmDep = null;
                if (jpmRef.getVersion() != null) {
                    jpmDep = db.getJpm(jpmRef.getName(), jpmRef.getVersion());
                } else {
                    jpmDep = db.getJpm(jpmRef.getName());
                }
                if (jpmDep != null) {
                    jpmDeps.add(jpmDep);
                }
            }
        }
        new GetCommand(jpmDeps).run();
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
