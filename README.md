# Java Package Manager

This is a package manager for Java. The main objective is to avoid redundant configuration as much as possible.
A typical JPM project has no extra configuration files describing dependencies, versions, etc. Yet these things all exist. How?!

## Version and Dependency Information

JPM recognizes tags in the version control system of your project of the form v\<major\>.\<minor\>.\<patch\>, according to the semantic versioning scheme. So there is no need to redundantly store it again in a file. Before any such tag has been set, the version is assumed to be 1.0.0. Since semantic versioning is used, version conflicts are automatically resolved when the same module is needed transitively of different versions, when possible. It is however not possible to resolve automatically, when different major versions of a library is required, as major versions denote backwards incompatible changes; then manual intervention is necessary.

Dependencies are stated in module-info.java files, describing your module dependencies. The module dependencies are the artifacts you depend on, so there is indeed no need to write in another file the same dependencies again.

Notably, module dependencies do not state a version number. So if you build without any version being stated, it will download the latest version of said dependencies. If you insist on running older versions of the dependencies, you can manually run:

$ jpm get foo --version v\<major\>.\<minor\>.\<patch\>

This will download a specific version of foo, and its transitive closure.

The JAR files that are downloaded have versions embedded in them, either in the file name, or embedded in
the --module-version of the JAR file, as well as in a separate generated main.jpm file. So they will be properly recognized.

During installation, all information about the project are available and can be stored into
a jpm file describing the project (project name, project version, dependency names and version). This
file is created automatically by the build system, without the need to maintain configuration files.
The jpm file ensures that that others cloning your repo will use the same versions of all dependencies,
and also serves as breadcrumbs when publishing to the central repository, to allow transitive dependency
resolution.

## Automatic Launchers

When the project is installed, launchers are automatically created for found main() functions, with launcher names equal to
the main class names. 

## File Structure

This project believes in convention over configuration. A project using jpm is structured like this:

src/main/java/module.name

src/main/java/module\.name/module-info.java // module with dependency information

src/main/java/module\.name/\*\*/\*.java // java source code

lib/main/\*.jar // Direct dependencies

lib/transitive/\*.jar // Transitive dependencies

The state of this file system describes the JPM, instead of a configuration file. The dependencies are whatever is in lib/main, and will be automatically populated from the module-info file.

Once a project is installed, the installed structure looks like this:

~/.jpm

~/.jpm/bin // launchers; add to $PATH

~/.jpm/lib // installed modules

~/.jpm/share/module.name // project specific information

The philosophy of this tool is to let each project have a local package manager, instead of a local configuration file.
You locally install JARs, placing them in a lib directory (with transitive dependencies). Dependency downloading is also automatic, given the dependencies of your module-info.java files.

## Installation

JPM itself is installed by running ./install.sh
It recursively uses JPM to install itself.

Make sure you add ~/.jpm/bin to $PATH

Now you have the jpm command.

## Use

$ jpm clean
* clean the project.

$ jpm get foo \[--version v\<major\>.\<minor\>.\<patch\>\]

$ jpm build
* builds a project using zero configuration.

$ jpm install
* installs a project using zero configuration

$ jpm publish jar_list
* upload jpm-build jars to the central repository
