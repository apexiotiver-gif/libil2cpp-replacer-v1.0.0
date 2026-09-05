# libil2cpp-replacer-v1.0.0

A Sketchware library that replaces `libil2cpp.so` inside the Free Fire Max APK using root access. The modified `.so` file is bundled inside `classes.jar` and extracted at runtime — no need to keep files in the Download folder.

## Features

- **Switch ON** (`replaceLib`): Extracts bundled `libil2cpp.so` from JAR, replaces it inside the game's split APK using root (`su`)
- **Switch OFF** (`restoreLib`): Restores the original `libil2cpp.so` from backup
- No Download folder dependency — everything is self-contained in the JAR
- Android 15 compatible (handles SELinux context, permissions, owner)
- Java 8 bytecode (Sketchware compatible)

## Requirements

- Rooted phone (Magisk/KernelSU)
- Free Fire Max installed (`com.dts.freefiremax`)
- `split_config.arm64_v8a.apk` split present

## Installation (Sketchware)

1. Import `classes.jar` via Library Manager
2. Add the library folder `libil2cpp-replacer-v1.0.0/` to Sketchware libs path
3. Add `libil2cpp.so` to `classes.jar` root level (JAR root, next to META-INF and mts folders)
4. Rebuild project

## Usage

```java
// Switch ON - Replace libil2cpp.so
libil2cpp_replacer.replaceLib(context);

// Switch OFF - Restore original
libil2cpp_replacer.restoreLib(context);
```

### With Switch (s4 example)

```java
s4ha.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
    @Override
    public void onCheckedChanged(CompoundButton _param1, boolean _param2) {
        if (_param2) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    libil2cpp_replacer.replaceLib(HomeActivity.this);
                }
            }).start();
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    libil2cpp_replacer.restoreLib(HomeActivity.this);
                }
            }).start();
        }
    }
});
```

## How It Works

1. `extractBundledLib()` — Extracts `libil2cpp.so` from JAR using `getResourceAsStream()`
2. `findApkPath()` — Finds the game's split APK path using `pm path`
3. `replaceInZip()` — Replaces the `.so` inside the APK (ZIP format)
4. Root commands handle backup, copy, permissions, SELinux context

## Package

- Package: `mts.com.proxy`
- Class: `libil2cpp_replacer`
- Min SDK: 21 (Android 5.0)
- Target SDK: 34 (Android 14)

## Library Structure

```
libil2cpp-replacer-v1.0.0/
├── classes.dex              (Dalvik bytecode)
├── classes.jar              (Java bytecode - add libil2cpp.so here)
├── AndroidManifest.xml
├── R.txt
├── proguard.txt
├── config/
│   └── library.info
└── res/
    ├── anim/
    │   └── accelerate_decelerate_interpolator.xml
    └── values/
        ├── strings.xml
        ├── styles.xml
        └── ids.xml
```

## classes.jar internal structure

```
classes.jar/
├── META-INF/
│   └── MANIFEST.MF
├── mts/
│   └── com/
│       └── proxy/
│           └── libil2cpp_replacer.class
└── libil2cpp.so              <-- Add this manually (186MB, too large for GitHub)
```

## Note

`libil2cpp.so` (186MB) is NOT included in this repo due to GitHub's 100MB file size limit. Add it manually to `classes.jar` root level after cloning.