hangr [![Build Status](http://img.shields.io/travis/dhleong/hangr.svg?style=flat)](https://travis-ci.org/dhleong/hangr)
=====

*Hangouts, the way it was meant to be*

## What?

![hangr-screenshot](https://cloud.githubusercontent.com/assets/816150/21584557/45b8f330-d07a-11e6-9f58-588cd6783070.png)

Hangr is an unofficial, "native" app (using [Electron](http://electron.atom.io/)) for Google Hangouts
that brings back the old Chrome extension UI with windows floating on the desktop, pinned to the bottom.

Hangr is currently in a very early stage of development, but does
support most basic features so far. If there's something missing
and there's not already an issue for it, open one! Or, better yet,
make a Pull Request!

## Contributing

Want to help out? Here's some things you might need to know:

### Requirements

* JDK 1.7+
* Leiningen 2.5.3
* node.js 6.5.0 [Or whatever version is compatible with Electron]

On Mac/Linux, installing node.js using [Node Version Manager](https://github.com/creationix/nvm) is recommended.

This project uses Electron. Please check [Electron's GitHub page](https://github.com/electron/electron) for the latest version. The version is specified in `Gruntfile.js` under the `Grunt Config` section.

### Setup

On Mac/Linux:

```
scripts/setup.sh
```

On Windows:

```
scripts\setup.bat
```

This will install the node dependencies for the project, along with grunt and bower and will also run `grunt setup`.


### Development mode

Start the figwheel server and repl:

```
scripts/repl
```

In another terminal window, launch the electron app:

```
grunt launch
```

You can edit the files under `src/cljs/hangr/` and the changes should show up in the electron app without the need to re-launch.
You can also connect to the repl using [Cider](https://github.com/clojure-emacs/cider-nrepl) via 
[vim-fireplace](https://github.com/tpope/vim-fireplace/) or whatever else you prefer.

### Dependencies

Node dependencies are in `package.json` file. Clojure/ClojureScript dependencies are in `project.clj`.

### Icons

Please replace the icons provided with your application's icons. The development icons are from [node-appdmg](https://github.com/LinusU/node-appdmg) project.

Files to replace:

* app/img/logo.icns
* app/img/logo.ico
* app/img/logo_96x96.png
* scripts/dmg/TestBkg.png
* scripts/dmg/TestBkg@2x.png

### Creating a build for release

To create a Windows build from a non-Windows platform, please install `wine`. On OS X, an easy option is using homebrew.

On Windows before doing a production build, please edit the `scripts/build-windows-exe.nsi` file. The file is the script for creating 
the [NSIS](http://nsis.sourceforge.net/)-based setup file.

On Mac OSX, please edit the variables for the plist in `release-mac` task in `Gruntfile.js`.

Using [`electron-packager`](https://github.com/maxogden/electron-packager), we are able to create a directory which has OS executables (.app, .exe etc) running from any platform.

If NSIS is available on the path, a further setup executable will be created for Windows. Further, if the release command is run from a OS X machine, a DMG file will be created.

To create the release directories:

```
grunt release
```

This will create the directories in the `builds` folder.

Note: you will need to be on OSX to create a DMG file and on Windows to create the setup .exe file.


### Grunt commands

To run a command, type `grunt <command>` in the terminal.


| Command       | Description                                                                               |
|---------------|-------------------------------------------------------------------------------------------|
| setup         | Download electron project, installs bower dependencies and setups up the app config file. |
| launch        | Launches the electron app                                                                 |
| release       | Creates a Win/OSX/Linux executables                                                       |
| check-old     | List all outdated clj/cljs/node/bower dependencies                                        |

### Leiningen commands

To run a command, type `lein <command>` in the terminal.

| Command       | Description                                                                               |
|---------------|-------------------------------------------------------------------------------------------|
| cljfmt fix    | Auto-formats all clj/cljs code. See [cljfmt](https://github.com/weavejester/cljfmt)       |
| kibit         | Statically analyse clj/cljs and give suggestions                                          |
