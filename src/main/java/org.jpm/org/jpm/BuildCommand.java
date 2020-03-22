package org.jpm;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class BuildCommand {
    private Project _project;
    private DependencyDetector _deps;
    private String _mainClass;

    public BuildCommand() {
        _project = new Project();
        _deps = new DependencyDetector(_project);
    }

    private Pattern _mainPattern = Pattern.compile(".*public static void main\\(java\\.lang\\.String\\[\\]\\).*");

    public boolean getMainClass(Path path) {
        try {
            var in = new FileInputStream(path.toString());
            var reader = new ClassReader(in);
            var classNode = new ClassNode();
            reader.accept(classNode, 0);
            for (var method: classNode.methods) {
                if (!method.name.equals("main")) {
                    return false;
                }
                if (method.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                  return false;
                }
                return true;
            }
        } catch (Exception e) {}
        return false;
    }

    public String getMainClass(List<Path> classFiles) {
        var moduleClassPath = _project.getBuildPath().resolve(_project.getProjectName());
        var pool = Executors.newCachedThreadPool();
        for (var file : classFiles) {
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
        Cmd.run("javac -d build --module-path lib --module-source-path src/main/java src/main/java/**/*.java --module " + name + " -source 11");
        String mainClass = getMainClass();
        Cmd.run("jar -c --module-version=" + version + " --file=build/" + name + "-" + version + ".jar --main-class=" + mainClass + " -C build/" + name + " .");
        System.out.println(_deps.getDependencies());
    }
}
