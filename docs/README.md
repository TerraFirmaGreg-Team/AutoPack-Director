# Modpack Director
![Mod Icon](logo.png)

With similar goals to [Mod Director](https://github.com/Janrupf/mod-director),
Modpack Director offers the ability
to download and process mods and files
at runtime.

Modpack Director gives you flexibility in situations
where a mod cannot be packed with your modpack
but can be downloaded at runtime either due to not being present on your target distribution platform,
launcher or copyright issues.

## Features
* Supports (Neo)Forge from 1.6.4 up to 1.20.4 (and probably most future versions)
* CurseForge Download (only third-party download enabled mods) 
* File Download
* Zip Unpacking
* File Renaming

## Usage
Modpack Director is distributed in three forms:
* **Standalone**: Self contained executable, you can execute it as a normal application
* **LauncherWrapper**: Bootstrap Modpack Director through LauncherWrapper, compatible with forge from 1.6.4 to 1.12.2
* **ModLauncher**: Bootstrap Modpack Director through ModLauncher, compatible with (neo)forge from 1.13.2 to latest

At the core, all distributions are exactly the same application,
being means to start the mod as early as possible in the game lifecycle.
Choose the one best suitable for you and your minecraft version.

For configuring the mod, please visit our [wiki](https://github.com/juanmuscaria/ModpackDirector/wiki).

## Planned Features
* Modrinth download support
* Fabric Loader support
* File patching
* Universal jar
* User defined UI themes
* Multi-language support
* Rework configuration
* User prompt showing all changes that will be made before modpack installation
* Configuration creator/editor GUI

## Credits
* [File Director](https://github.com/TerraFirmaCraft-The-Final-Frontier/FileDirector) - File Processing Features
* [Mod Director](https://github.com/Janrupf/mod-director) - Original Project
* [Material UI Swing](https://github.com/vincenzopalazzo/material-ui-swing) - UI Theming

## Demo
![Modpack Director in action](demo.webp)