# jpm
Java Package Manager

This is a package manager for Java. The main objective is to avoid redundant configuration as much as possible.
A typical project has no configuration file describing dependencies, versions, etc. Yet these things all exist.
The version is stored as tags in the version control system, and dependencies are stated in module-info.java files.
There is no need to state them again.

The JAR files that comprise the dependencies have versions embedded in them, either in the file name, or embedded in
the --module-version of the JAR file. These are the versions of the dependencies.

When the project is installed, launchers are automatically created for main() functions, with command names equal to
the main class names. During installation, all information about the project are available and can be stored into
a jpm file describing the project (project name, command names, project version, dependency names and version). This
file is created automatically by the system, without the need to maintain configuration files.

The philosophy of this tool is to let each project have a local package manager. You locally install JARs, placing them in a
lib directory (with transitive dependencies). Version conflicts are resolved automatically to the extent possible with
semantic versioning. You ultimately end up with a set of dependencies on the lib directory. When you install software
into the global repository, all libs are placed in a global ~/.jpm/slib folder, and all launchers in a global ~/.jpm/sbin 
folder that you should add to $PATH. Application data is in a ~/.jpm/share/your_app folder.
