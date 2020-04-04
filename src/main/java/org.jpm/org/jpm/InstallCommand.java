package org.jpm;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class InstallCommand {
    private Project _project = new Project();
    private Repository _repo = new Repository();
    private File _mainJar;
    private DependencyDetector _deps;

    public void run() {
        if (!_project.getBuildPath().resolve(_project.getProjectJarName()).toFile().exists()) {
            new BuildCommand().run();
        }
        _deps = new DependencyDetector(_project);
        installLibs();
        installLauncher();
    }

    private void installJar(Path src, Path dst, String fileName) {
        System.out.println("Copying " + src + " to " + dst);
        var sharePath = _repo.getSharePath(_project);
        var shareLib = sharePath.resolve("lib");
        shareLib.toFile().mkdirs();
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            var shareLibPath = shareLib.resolve(fileName);
            if (shareLibPath.toFile().exists()) {
                shareLibPath.toFile().delete();
            }
            Files.createSymbolicLink(shareLibPath, dst);
        } catch (IOException e) {
            throw new RuntimeException("Could not install: ", e);
        }
        try (JpmDatabase db = JpmDatabase.localDatabase()) {
            var jpmFile = JpmFile.fromJar(src);
            System.out.println("Adding jpm to local database: " + jpmFile.getMain().getName() + "-" + jpmFile.getMain().getVersion());
            db.addJpm(jpmFile);
        }
    }

    private void installLibs(String libPath) {
        var finder = ModuleFinder.of(_project.getLibraryPath().resolve(libPath));
        String mainModule = _project.getProjectName();
        for (var module: finder.findAll()) {
            var descriptor = module.descriptor();
            var name = descriptor.name();
            var version = _deps.getVersion(name);
            var fileName = name;
            if (version != null) {
                fileName += "-" + version;
            } else {
                throw new RuntimeException("Can't install " + name + " without version");
            }
            fileName += ".jar";
            var src = Paths.get(module.location().get());
            var dst = _repo.getLibraryPath().resolve(fileName);
            installJar(src, dst, fileName);
            if (mainModule.equals(name)) {
                _mainJar = dst.toFile();
            }
        }
    }

    private void installLibs() {
        installLibs("main");
        installLibs("transitive");
        var targetJar = _project.getProjectJarName();
        var src = _project.getBuildPath().resolve(targetJar);
        var dst = _repo.getLibraryPath().resolve(targetJar);
        _mainJar = dst.toFile();
        installJar(src, dst, targetJar);
    }

    private void installLauncher() {
        try {
            JarFile j = new JarFile(_mainJar);
            String mainClass = j.getManifest().getMainAttributes().getValue("Main-Class");

            if (mainClass == null) {
                return;
            }

            String mainModule = _project.getProjectName();
            String[] mainClassComponents = mainClass.toLowerCase().split("\\.");
            String executable = mainClassComponents[mainClassComponents.length - 1];

            var sharePath = _repo.getSharePath(_project);
            Path launcherArgsPath = sharePath.resolve("args");
            Path launcherSourcePath = _repo.getBinaryPath().resolve(executable);

            String launcherArgs = "--module-path=" + _repo.getLibraryPath() + " -m " + mainModule;
            String launcherSource = "#!/usr/bin/java @" + launcherArgsPath;

            Files.write(launcherArgsPath, launcherArgs.getBytes(Charset.defaultCharset()));
            Files.write(launcherSourcePath, launcherSource.getBytes(Charset.defaultCharset()));
            Cmd.run("chmod +x " + launcherSourcePath.toString());
        } catch (IOException e) {
            throw new RuntimeException("Could not install launcher: ", e);
        }
    }
}
