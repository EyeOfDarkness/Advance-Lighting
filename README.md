## Intermod Support

This mod allows other mods to have glowing sprites without needing a dependency.
There are 2 ways to opt-in: "Internal" and "External"

**Internal:** This means that the sprites are in the base mod itself. sprites must end with a suffix `-advance-light` for sprites that glows partially, `-advance-light-replace` to replace sprites, used for certain buildings that has gloss.

**External:** A separate mod, the mod must have the name of the base mod with the prefix `advance-lighting-`. for sprites that partially glows no longer need a suffix and have the same
  name as the base sprite, sprites to be replaced use `-replace` as a suffix.

---

Parts:
Parts used by units and turrets, can have an extra sprite if the part heats up.

Internal: `-advance-light-heat` suffix if the part doesnt have turret shading. if the part use turret shading `-advance-light-heat-l`, `-advance-light-heat-r` for left and right respectively

External: `-heat`, `-heat-l`, `-heat-r`. same as above with different suffix.

---

Limitations:
Currently, the mod doesnt doesnt iterate through every sprite in the game. It iterates through blocks and units, and won't be able to load certain sprites if the mod doesnt support it, or if the other mod uses custom classes that stores textures differently.

You also cant make full glow sprites without unnecessarily rendering the bottom sprite.

Sprite replacement only works on blocks, and only the block's main region, or conduits and ducts.

## Building for Desktop Testing

1. Install JDK **17**.
2. Run `gradlew jar` [1].
3. Your mod jar will be in the `build/libs` directory. **Only use this version for testing on desktop. It will not work with Android.**
To build an Android-compatible version, you need the Android SDK. You can either let Github Actions handle this, or set it up yourself. See steps below.

## Building through Github Actions

This repository is set up with Github Actions CI to automatically build the mod for you every commit. This requires a Github repository, for obvious reasons.
To get a jar file that works for every platform, do the following:
1. Make a Github repository with your mod name, and upload the contents of this repo to it. Perform any modifications necessary, then commit and push. 
2. Check the "Actions" tab on your repository page. Select the most recent commit in the list. If it completed successfully, there should be a download link under the "Artifacts" section. 
3. Click the download link (should be the name of your repo). This will download a **zipped jar** - **not** the jar file itself [2]! Unzip this file and import the jar contained within in Mindustry. This version should work both on Android and Desktop.

## Building Locally

Building locally takes more time to set up, but shouldn't be a problem if you've done Android development before.
1. Download the Android SDK, unzip it and set the `ANDROID_HOME` environment variable to its location.
2. Make sure you have API level 30 installed, as well as any recent version of build tools (e.g. 30.0.1)
3. Add a build-tools folder to your PATH. For example, if you have `30.0.1` installed, that would be `$ANDROID_HOME/build-tools/30.0.1`.
4. Run `gradlew deploy`. If you did everything correctlly, this will create a jar file in the `build/libs` directory that can be run on both Android and desktop. 

## Adding Dependencies

Please note that all dependencies on Mindustry, Arc or its submodules **must be declared as compileOnly in Gradle**. Never use `implementation` for core Mindustry or Arc dependencies. 

- `implementation` **places the entire dependency in the jar**, which is, in most mod dependencies, very undesirable. You do not want the entirety of the Mindustry API included with your mod.
- `compileOnly` means that the dependency is only around at compile time, and not included in the jar.

Only use `implementation` if you want to package another Java library *with your mod*, and that library is not present in Mindustry already.

--- 

*[1]* *On Linux/Mac it's `./gradlew`, but if you're using Linux I assume you know how to run executables properly anyway.*  
*[2]: Yes, I know this is stupid. It's a Github UI limitation - while the jar itself is uploaded unzipped, there is currently no way to download it as a single file.*
