#!/bin/bash
# Pre-download LWJGL 3.3.3 JARs to a local Maven repository structure
# This is a workaround for Docker builds where Loom can't resolve LWJGL from libraries.minecraft.net

set -e

LWJGL_VERSION="3.3.3"
BASE_URL="https://libraries.minecraft.net/org/lwjgl"
REPO_DIR="/iconexporter/local-maven-repo"

# LWJGL modules needed by Minecraft
MODULES=(
    "lwjgl"
    "lwjgl-freetype"
    "lwjgl-glfw"
    "lwjgl-jemalloc"
    "lwjgl-openal"
    "lwjgl-opengl"
    "lwjgl-stb"
    "lwjgl-tinyfd"
)

# Platform natives (only Linux for Docker)
NATIVES="natives-linux"

echo "=== Setting up local LWJGL Maven repository ==="
echo "Repository directory: $REPO_DIR"

for MODULE in "${MODULES[@]}"; do
    MODULE_DIR="$REPO_DIR/org/lwjgl/$MODULE/$LWJGL_VERSION"
    mkdir -p "$MODULE_DIR"

    # Download main JAR
    JAR_URL="$BASE_URL/$MODULE/$LWJGL_VERSION/$MODULE-$LWJGL_VERSION.jar"
    JAR_FILE="$MODULE_DIR/$MODULE-$LWJGL_VERSION.jar"

    echo "Downloading $MODULE-$LWJGL_VERSION.jar..."
    curl -sL "$JAR_URL" -o "$JAR_FILE"

    # Download POM
    POM_URL="$BASE_URL/$MODULE/$LWJGL_VERSION/$MODULE-$LWJGL_VERSION.pom"
    POM_FILE="$MODULE_DIR/$MODULE-$LWJGL_VERSION.pom"
    echo "Downloading $MODULE-$LWJGL_VERSION.pom..."
    curl -sL "$POM_URL" -o "$POM_FILE"

    # Download module file if exists
    MODULE_FILE_URL="$BASE_URL/$MODULE/$LWJGL_VERSION/$MODULE-$LWJGL_VERSION.module"
    MODULE_FILE="$MODULE_DIR/$MODULE-$LWJGL_VERSION.module"
    if curl -sIf "$MODULE_FILE_URL" > /dev/null 2>&1; then
        echo "Downloading $MODULE-$LWJGL_VERSION.module..."
        curl -sL "$MODULE_FILE_URL" -o "$MODULE_FILE"
    fi

    # Download Linux natives JAR
    NATIVE_URL="$BASE_URL/$MODULE/$LWJGL_VERSION/$MODULE-$LWJGL_VERSION-$NATIVES.jar"
    NATIVE_FILE="$MODULE_DIR/$MODULE-$LWJGL_VERSION-$NATIVES.jar"
    if curl -sIf "$NATIVE_URL" > /dev/null 2>&1; then
        echo "Downloading $MODULE-$LWJGL_VERSION-$NATIVES.jar..."
        curl -sL "$NATIVE_URL" -o "$NATIVE_FILE"
    fi

    # Generate SHA1 checksums (Gradle needs these)
    for f in "$MODULE_DIR"/*; do
        if [ -f "$f" ] && [[ ! "$f" == *.sha1 ]]; then
            sha1sum "$f" | awk '{print $1}' > "$f.sha1"
        fi
    done
done

echo "=== LWJGL local repository setup complete ==="
find "$REPO_DIR" -type f | head -30
