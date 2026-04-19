# Building from Source

## Prerequisites

- JDK 17+
- Maven 3.9+
- **Windows:** Visual Studio 2019+ with DirectX SDK

## Build

### Windows

```bash
compile.bat
mvn clean package
```

Or use the PowerShell build script:

```powershell
.\build.ps1
```

The build script compiles the native DirectX backend and packages it with the JAR.

## Run Examples

```bash
# Run specific demo
run.bat

# Or run individual demos
run_drawline.bat
run_drawrect.bat
run_drawoval.bat
run_imagezoom.bat
```

## Installation

### JitPack (Recommended)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fastgraphics</artifactId>
        <version>v1.3.1</version>
    </dependency>
</dependencies>
```

### Gradle (JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:fastgraphics:v1.3.1'
}
```

## Download Pre-built JAR

See [Releases Page](https://github.com/andrestubbe/FastGraphics/releases)

## Troubleshooting

### JNI UnsatisfiedLinkError

If you get `UnsatisfiedLinkError`, the native library was not found:

1. Check that `fastgraphics.dll` exists after running `compile.bat`
2. Ensure the DLL is in PATH or copy to `C:\Windows\System32`
3. Verify DirectX Runtime is installed

### Graphics not rendering

- Verify GPU drivers are up to date
- Check DirectX 11 compatibility
- Run with `-Djava.awt.headless=false` if using with Swing
