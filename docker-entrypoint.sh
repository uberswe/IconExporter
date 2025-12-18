#!/bin/bash
set -euo pipefail

# ============================================================================
# IconExporter Docker Entrypoint
# Orchestrates Xvfb, Minecraft client, and auto-export
# Uses pre-exported runtime (no Gradle needed at container runtime)
# ============================================================================

readonly SCRIPT_VERSION="4.0.0"
readonly RUNTIME_DIR="/iconexporter/runtime"
readonly RUN_DIR="$RUNTIME_DIR/run"
readonly INPUT_MODS_DIR="/iconexporter/input-mods"
readonly EXPORTS_DIR="/iconexporter/exports"
readonly COMPLETION_MARKER="$EXPORTS_DIR/.export_complete"
readonly ERROR_MARKER="$EXPORTS_DIR/.export_error"
readonly LOG_FILE="$EXPORTS_DIR/docker-export.log"

# Production mode: Uses intermediary mappings and loads mods from mods/ folder
# Development mode: Uses named mappings and loads mods from classpath
readonly PRODUCTION_MODE="${ICONEXPORTER_PRODUCTION_MODE:-true}"

# Logging functions
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" | tee -a "$LOG_FILE" >&2
}

# Cleanup function
cleanup() {
    local exit_code=$?
    log "Cleaning up (exit code: $exit_code)"

    # Kill Xvfb if running
    if [ -n "${XVFB_PID:-}" ]; then
        log "Stopping Xvfb (PID: $XVFB_PID)"
        kill $XVFB_PID 2>/dev/null || true
        wait $XVFB_PID 2>/dev/null || true
    fi

    # Kill Minecraft if running
    if [ -n "${MC_PID:-}" ]; then
        log "Stopping Minecraft (PID: $MC_PID)"
        kill $MC_PID 2>/dev/null || true
        sleep 2
        kill -9 $MC_PID 2>/dev/null || true
    fi

    exit $exit_code
}

trap cleanup EXIT INT TERM

# ============================================================================
# Step 1: Validation
# ============================================================================

validate_environment() {
    log "Validating environment..."

    # Create exports directory
    mkdir -p "$EXPORTS_DIR"

    # Check runtime directory
    if [ ! -d "$RUNTIME_DIR/libs" ]; then
        log_error "Runtime directory not found: $RUNTIME_DIR/libs"
        log_error "Did you run './gradlew :loader-fabric:exportDockerRunScript' before building the image?"
        exit 1
    fi

    # Check input mods directory
    if [ ! -d "$INPUT_MODS_DIR" ]; then
        log "Warning: Input mods directory not found: $INPUT_MODS_DIR"
        log "Creating empty mods directory..."
        mkdir -p "$INPUT_MODS_DIR"
    fi

    # Count input mods
    mod_count=$(find "$INPUT_MODS_DIR" -name "*.jar" 2>/dev/null | wc -l)
    log "Found $mod_count user mod JAR(s) in input-mods"

    # Clean previous run markers
    rm -f "$COMPLETION_MARKER" "$ERROR_MARKER"

    log "Environment validation complete"
}

# ============================================================================
# Step 2: Download Linux LWJGL Natives
# ============================================================================

download_linux_natives() {
    log "Downloading Linux LWJGL natives..."

    local arch=$(uname -m)
    local native_classifier
    case "$arch" in
        x86_64|amd64)
            native_classifier="linux"
            ;;
        aarch64|arm64)
            native_classifier="linux-arm64"
            ;;
        *)
            log_error "Unsupported architecture: $arch"
            exit 1
            ;;
    esac

    log "  Architecture: $arch -> LWJGL classifier: natives-$native_classifier"

    local lwjgl_version="3.3.3"
    local libs_dir="$RUNTIME_DIR/libs"
    local maven_url="https://repo1.maven.org/maven2/org/lwjgl"

    # LWJGL modules that require native libraries
    local modules=(
        "lwjgl"
        "lwjgl-freetype"
        "lwjgl-glfw"
        "lwjgl-jemalloc"
        "lwjgl-openal"
        "lwjgl-opengl"
        "lwjgl-stb"
        "lwjgl-tinyfd"
    )

    for module in "${modules[@]}"; do
        local native_jar="${module}-${lwjgl_version}-natives-${native_classifier}.jar"
        local native_path="${libs_dir}/${native_jar}"

        if [ -f "$native_path" ]; then
            log "  Cached: $native_jar"
            continue
        fi

        local url="${maven_url}/${module}/${lwjgl_version}/${native_jar}"
        log "  Downloading: $native_jar"
        curl -sSL --fail --location -o "$native_path" "$url" || {
            log_error "Failed to download $native_jar from $url"
            # Try without fail flag to see if it's a 404 or connection issue
            log "  Attempting fallback..."
            continue
        }
        log "    Size: $(stat -c%s "$native_path" 2>/dev/null || stat -f%z "$native_path") bytes"
    done

    log "Linux LWJGL natives download complete"
}

# ============================================================================
# Step 2b: Download Production Mod JARs from Modrinth
# ============================================================================

download_production_mods() {
    log "Downloading production mod JARs from Modrinth..."

    local mods_dir="$RUN_DIR/mods"
    local mc_version="1.21.1"

    # Remove development JARs that have incorrect namespace (named instead of intermediary)
    # This includes CyclopsCore, IconExporter, and all Fabric API modules
    log "  Removing development JARs with incorrect namespace..."
    rm -f "$mods_dir"/cyclopscore-*.jar 2>/dev/null || true
    # NOTE: We keep the bundled iconexporter JAR - it's been remapped to intermediary
    # and contains the auto-export features not yet published to Modrinth
    # Remove all individual Fabric API development module JARs
    # (but NOT forgeconfigapiport which is a separate mod)
    rm -f "$mods_dir"/fabric-api-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-biome-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-block-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-blockrenderlayer-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-client-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-command-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-commands-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-content-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-convention-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-crash-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-data-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-dimensions-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-entity-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-events-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-game-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-gametest-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-item-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-key-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-keybindings-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-lifecycle-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-loot-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-message-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-model-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-networking-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-object-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-particles-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-recipe-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-registry-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-renderer-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-rendering-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-resource-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-screen-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-sound-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-transfer-*.jar 2>/dev/null || true
    rm -f "$mods_dir"/fabric-transitive-*.jar 2>/dev/null || true
    # Remove forgeconfigapiport development JAR (has named mappings)
    rm -f "$mods_dir"/forgeconfigapiport-*.jar 2>/dev/null || true

    # Download Fabric API from Modrinth
    # Project ID: P7dR8mSH (slug: fabric-api)
    log "  Fetching Fabric API version info from Modrinth..."
    local fabric_versions=$(curl -sSL "https://api.modrinth.com/v2/project/P7dR8mSH/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22${mc_version}%22%5D")

    local fabric_url=$(echo "$fabric_versions" | python3 -c "
import sys, json
versions = json.load(sys.stdin)
if versions:
    for f in versions[0]['files']:
        if f['primary']:
            print(f['url'])
            break
")

    if [ -n "$fabric_url" ]; then
        local fabric_filename=$(basename "$fabric_url")
        log "  Downloading: $fabric_filename"
        curl -sSL --fail --location -o "$mods_dir/$fabric_filename" "$fabric_url" || {
            log_error "Failed to download Fabric API from Modrinth"
            exit 1
        }
        log "    Size: $(stat -c%s "$mods_dir/$fabric_filename" 2>/dev/null || stat -f%z "$mods_dir/$fabric_filename") bytes"
    else
        log_error "Could not find Fabric API for Minecraft ${mc_version} on Modrinth"
        exit 1
    fi

    # Download CyclopsCore from Modrinth
    # Project ID: Z9DM0LJ4 (from build.gradle)
    log "  Fetching CyclopsCore version info from Modrinth..."
    local cyclops_versions=$(curl -sSL "https://api.modrinth.com/v2/project/Z9DM0LJ4/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22${mc_version}%22%5D")

    local cyclops_url=$(echo "$cyclops_versions" | python3 -c "
import sys, json
versions = json.load(sys.stdin)
if versions:
    for f in versions[0]['files']:
        if f['primary']:
            print(f['url'])
            break
")

    if [ -n "$cyclops_url" ]; then
        local cyclops_filename=$(basename "$cyclops_url")
        log "  Downloading: $cyclops_filename"
        curl -sSL --fail --location -o "$mods_dir/$cyclops_filename" "$cyclops_url" || {
            log_error "Failed to download CyclopsCore from Modrinth"
            exit 1
        }
        log "    Size: $(stat -c%s "$mods_dir/$cyclops_filename" 2>/dev/null || stat -f%z "$mods_dir/$cyclops_filename") bytes"
    else
        log_error "Could not find CyclopsCore for Minecraft ${mc_version} on Modrinth"
        exit 1
    fi

    # NOTE: IconExporter is NOT downloaded from Modrinth because the published version
    # doesn't include the auto-export features (WorldAutoLoader, AutoExportHandler).
    # We use the bundled iconexporter-*-DEV.jar which has been remapped to intermediary.
    log "  Using bundled IconExporter JAR (contains auto-export features)"

    # Download Forge Config API Port from Modrinth
    # Project ID: ohNO6lps (slug: forge-config-api-port)
    # NOTE: We need version 21.1.0 specifically - newer versions require Fabric Loader 0.16.1+
    log "  Fetching Forge Config API Port version info from Modrinth..."
    local fcap_versions=$(curl -sSL "https://api.modrinth.com/v2/project/ohNO6lps/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22${mc_version}%22%5D")

    local fcap_url=$(echo "$fcap_versions" | python3 -c "
import sys, json
versions = json.load(sys.stdin)
# Find version 21.1.0 specifically (compatible with Fabric Loader 0.16.0)
target_version = 'v21.1.0-1.21.1-Fabric'
for v in versions:
    if v['version_number'] == target_version:
        for f in v['files']:
            if f['primary']:
                print(f['url'])
                break
        break
else:
    # Fallback to latest if not found
    if versions:
        for f in versions[0]['files']:
            if f['primary']:
                print(f['url'])
                break
")

    if [ -n "$fcap_url" ]; then
        local fcap_filename=$(basename "$fcap_url")
        log "  Downloading: $fcap_filename"
        curl -sSL --fail --location -o "$mods_dir/$fcap_filename" "$fcap_url" || {
            log_error "Failed to download Forge Config API Port from Modrinth"
            exit 1
        }
        log "    Size: $(stat -c%s "$mods_dir/$fcap_filename" 2>/dev/null || stat -f%z "$mods_dir/$fcap_filename") bytes"
    else
        log_error "Could not find Forge Config API Port for Minecraft ${mc_version} on Modrinth"
        exit 1
    fi

    log "Production mod JARs download complete"
}

# ============================================================================
# Step 2c: Download Production Minecraft Client JAR
# ============================================================================

download_minecraft_client() {
    log "Downloading production Minecraft client JAR..."

    local mc_version="1.21.1"
    local libs_dir="$RUNTIME_DIR/libs"
    local mc_jar="$libs_dir/minecraft-client-${mc_version}.jar"

    # Check if already cached
    if [ -f "$mc_jar" ]; then
        log "  Cached: minecraft-client-${mc_version}.jar"
        return 0
    fi

    # Fetch version manifest to get the client JAR URL
    log "  Fetching Mojang version manifest..."
    local version_manifest_url="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    local version_manifest=$(curl -sSL "$version_manifest_url")

    # Extract URL for the specific version
    local version_url=$(echo "$version_manifest" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for v in data['versions']:
    if v['id'] == '${mc_version}':
        print(v['url'])
        break
")

    if [ -z "$version_url" ]; then
        log_error "Could not find Minecraft version ${mc_version} in manifest"
        exit 1
    fi

    # Fetch version details to get client JAR URL
    log "  Fetching version details..."
    local version_details=$(curl -sSL "$version_url")
    local client_url=$(echo "$version_details" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['downloads']['client']['url'])
")
    local client_sha1=$(echo "$version_details" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['downloads']['client']['sha1'])
")

    if [ -z "$client_url" ]; then
        log_error "Could not find client JAR URL for Minecraft ${mc_version}"
        exit 1
    fi

    # Download client JAR
    log "  Downloading: minecraft-client-${mc_version}.jar"
    curl -sSL --fail --location -o "$mc_jar" "$client_url" || {
        log_error "Failed to download Minecraft client JAR"
        exit 1
    }

    # Verify SHA1
    local actual_sha1=$(sha1sum "$mc_jar" | cut -d' ' -f1)
    if [ "$actual_sha1" != "$client_sha1" ]; then
        log_error "SHA1 mismatch for Minecraft client JAR"
        log_error "  Expected: $client_sha1"
        log_error "  Actual:   $actual_sha1"
        rm -f "$mc_jar"
        exit 1
    fi

    log "  Size: $(stat -c%s "$mc_jar" 2>/dev/null || stat -f%z "$mc_jar") bytes"
    log "  SHA1: $actual_sha1 (verified)"
    log "Production Minecraft client download complete"
}

# ============================================================================
# Step 2d: Download Intermediary Mappings
# ============================================================================

download_intermediary_mappings() {
    log "Downloading intermediary mappings from Fabric Maven..."

    local mc_version="1.21.1"
    local libs_dir="$RUNTIME_DIR/libs"
    local intermediary_jar="$libs_dir/intermediary-${mc_version}.jar"

    # Check if already cached
    if [ -f "$intermediary_jar" ]; then
        log "  Cached: intermediary-${mc_version}.jar"
        return 0
    fi

    # Download from Fabric Maven
    local maven_url="https://maven.fabricmc.net/net/fabricmc/intermediary/${mc_version}/intermediary-${mc_version}.jar"
    log "  Downloading: intermediary-${mc_version}.jar"
    curl -sSL --fail --location -o "$intermediary_jar" "$maven_url" || {
        log_error "Failed to download intermediary mappings from Fabric Maven"
        exit 1
    }

    log "  Size: $(stat -c%s "$intermediary_jar" 2>/dev/null || stat -f%z "$intermediary_jar") bytes"
    log "Intermediary mappings download complete"
}

# ============================================================================
# Step 3: Configure Minecraft
# ============================================================================

configure_minecraft() {
    log "Configuring Minecraft..."
    log "Running in $([ "$PRODUCTION_MODE" = "true" ] && echo "PRODUCTION" || echo "DEVELOPMENT") mode"

    # Create run directory structure
    mkdir -p "$RUN_DIR/config"
    mkdir -p "$RUN_DIR/mods"
    mkdir -p "$RUN_DIR/saves"
    mkdir -p "$RUN_DIR/versions/1.21.1"

    # Create symlink from game directory's exports folder to the mounted volume
    # This ensures all exports end up in the mounted volume regardless of config parsing
    rm -rf "$RUN_DIR/exports" 2>/dev/null || true
    ln -sf "$EXPORTS_DIR" "$RUN_DIR/exports"
    log "Created symlink: $RUN_DIR/exports -> $EXPORTS_DIR"

    # Also create symlinks for legacy icon export paths (icon-exports-x{scale})
    # These are used when useStructuredOutput=false
    for scale in 16 32 64 128 256 512; do
        rm -rf "$RUN_DIR/icon-exports-x${scale}" 2>/dev/null || true
        mkdir -p "$EXPORTS_DIR/icon-exports-x${scale}"
        ln -sf "$EXPORTS_DIR/icon-exports-x${scale}" "$RUN_DIR/icon-exports-x${scale}"
    done
    log "Created legacy icon export symlinks"

    # Download Linux LWJGL natives (macOS natives come from Gradle export)
    download_linux_natives

    if [ "$PRODUCTION_MODE" = "true" ]; then
        # PRODUCTION MODE: Mods are loaded from the mods/ folder
        # This supports loading user mods with intermediary mappings
        log "Production mode: Mods will be loaded from mods/ folder"

        # Copy bundled mods from runtime/mods to run/mods
        if [ -d "$RUNTIME_DIR/mods" ]; then
            log "Copying bundled mods to run/mods/..."
            cp -v "$RUNTIME_DIR/mods/"*.jar "$RUN_DIR/mods/" 2>/dev/null || true
            local bundled_count=$(ls -1 "$RUN_DIR/mods/"*.jar 2>/dev/null | wc -l)
            log "  Copied $bundled_count bundled mod(s)"
        else
            log_error "Production mods directory not found: $RUNTIME_DIR/mods"
            log_error "Did you run './gradlew :loader-fabric:exportDockerProductionRuntime' before building?"
            exit 1
        fi

        # Download production mod JARs from Modrinth
        # This replaces the development JARs (with named mappings) with
        # production JARs (with intermediary mappings)
        download_production_mods

        # Copy user mods from input-mods to run/mods
        if [ -d "$INPUT_MODS_DIR" ]; then
            local user_mod_count=$(find "$INPUT_MODS_DIR" -name "*.jar" 2>/dev/null | wc -l)
            if [ "$user_mod_count" -gt 0 ]; then
                log "Copying $user_mod_count user mod(s) from input-mods/..."
                for mod_jar in "$INPUT_MODS_DIR"/*.jar; do
                    if [ -f "$mod_jar" ]; then
                        local mod_name=$(basename "$mod_jar")
                        cp -v "$mod_jar" "$RUN_DIR/mods/$mod_name"
                        log "  Copied: $mod_name"
                    fi
                done
            else
                log "No user mods found in input-mods/"
            fi
        fi

        # Download production Minecraft client JAR from Mojang
        download_minecraft_client

        # Download intermediary mappings from Fabric Maven
        # This is required for Fabric Loader to remap between obfuscated game and intermediary mods
        download_intermediary_mappings
    else
        # DEVELOPMENT MODE: Mods are loaded from the classpath, NOT the mods/ folder
        # Mods in mods/ folder would be remapped from intermediary to named, but our dev mods
        # are already in named namespace, causing "Namespace mismatch" errors.
        log "Development mode: Mods will be loaded from classpath (not mods/ folder)"

        # Verify required dev mods exist in libs (they'll be on classpath)
        local cyclops_jar=$(find "$RUNTIME_DIR/libs" -name "cyclopscore-*-fabric-*.jar" ! -name "*-sources*" | head -1)
        if [ -z "$cyclops_jar" ] || [ ! -f "$cyclops_jar" ]; then
            log_error "CyclopsCore not found in libs"
            exit 1
        fi
        log "  Found CyclopsCore: $(basename "$cyclops_jar")"

        local ie_jar=$(find "$RUNTIME_DIR/libs" -name "iconexporter-*-fabric-*.jar" ! -name "*-sources*" ! -name "*-deobf*" | head -1)
        if [ -z "$ie_jar" ] || [ ! -f "$ie_jar" ]; then
            log_error "IconExporter not found in libs"
            exit 1
        fi
        log "  Found IconExporter: $(basename "$ie_jar")"

        # User mods are not supported in development mode
        if [ -d "$INPUT_MODS_DIR" ] && [ "$(ls -A "$INPUT_MODS_DIR"/*.jar 2>/dev/null)" ]; then
            log "WARNING: User mods in input-mods/ are not loaded in development mode"
            log "         Set ICONEXPORTER_PRODUCTION_MODE=true to load user mods"
        fi
    fi

    # Generate IconExporter config
    local config_file="$RUN_DIR/config/iconexporter-client.toml"
    log "Generating config: $config_file"

    cat > "$config_file" << EOF
# IconExporter Client Configuration
# Auto-generated by Docker entrypoint

[core]
    # Export scale
    defaultScale = ${ICONEXPORTER_SCALE:-32}

    # Use structured output
    useStructuredOutput = true
    exportBaseDir = "/iconexporter/exports"

    # Automation settings
    autoExportOnStartup = true
    autoCreateWorld = true
    autoQuitAfterExport = true
    autoExportDelay = ${ICONEXPORTER_AUTO_DELAY:-30}

    # Modpack settings
    modpackName = "${ICONEXPORTER_MODPACK_NAME:-modpack}"

    # File naming
    fileNameHashComponents = false
EOF

    # Generate options.txt for Minecraft settings
    local options_file="$RUN_DIR/options.txt"
    log "Generating Minecraft options: $options_file"

    cat > "$options_file" << EOF
version:3465
autoJump:false
operatorItemsTab:false
enableVsync:false
graphicsMode:0
renderDistance:2
simulationDistance:2
maxFps:60
pauseOnLostFocus:false
fullscreen:false
renderClouds:"false"
guiScale:2
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
EOF

    # Create eula.txt (required for Minecraft to run without prompts)
    echo "eula=true" > "$RUN_DIR/eula.txt"

    log "Minecraft configuration complete"
}

# ============================================================================
# Step 3: Start Xvfb
# ============================================================================

start_xvfb() {
    log "Starting Xvfb on display $DISPLAY..."

    # Create temp directory for X11 socket
    mkdir -p /tmp/.X11-unix 2>/dev/null || true

    # Start Xvfb with appropriate settings
    Xvfb $DISPLAY -screen 0 1280x720x24 +extension GLX &
    XVFB_PID=$!

    log "Xvfb started (PID: $XVFB_PID)"

    # Wait for Xvfb to be ready
    for i in {1..10}; do
        if xdpyinfo -display $DISPLAY >/dev/null 2>&1; then
            log "Xvfb is ready"
            return 0
        fi
        log "Waiting for Xvfb... ($i/10)"
        sleep 1
    done

    log_error "Xvfb failed to start"
    exit 1
}

# ============================================================================
# Step 4: Launch Minecraft
# ============================================================================

launch_minecraft() {
    log "Launching Minecraft..."

    cd "$RUNTIME_DIR"

    # Fabric main class
    local main_class="net.fabricmc.loader.impl.launch.knot.KnotClient"
    local classpath=""
    local mc_jar=""
    local jvm_args=""

    # Add Linux native JARs to classpath (they were downloaded at runtime)
    local arch=$(uname -m)
    local native_classifier
    case "$arch" in
        x86_64|amd64) native_classifier="linux" ;;
        aarch64|arm64) native_classifier="linux-arm64" ;;
        *) native_classifier="linux" ;;
    esac

    if [ "$PRODUCTION_MODE" = "true" ]; then
        # PRODUCTION MODE: Load mods from mods/ folder, use production client JAR
        log "Running in PRODUCTION mode (intermediary mappings)"

        # Build classpath from the production classpath file (libraries only)
        local classpath_file="${RUNTIME_DIR}/classpath-production.txt"
        if [ ! -f "$classpath_file" ]; then
            log_error "Production classpath file not found: $classpath_file"
            log_error "Did you run './gradlew :loader-fabric:exportDockerProductionRuntime' before building?"
            exit 3
        fi

        while IFS= read -r line || [[ -n "$line" ]]; do
            if [ -n "$line" ]; then
                classpath="${classpath}:${RUNTIME_DIR}/${line}"
            fi
        done < "$classpath_file"
        classpath="${classpath:1}" # Remove leading colon

        # Add Linux native JARs to classpath
        for native_jar in "$RUNTIME_DIR/libs/"*-natives-${native_classifier}.jar; do
            if [ -f "$native_jar" ]; then
                classpath="${classpath}:${native_jar}"
            fi
        done

        # Add intermediary mappings JAR to classpath
        local intermediary_jar="$RUNTIME_DIR/libs/intermediary-1.21.1.jar"
        if [ -f "$intermediary_jar" ]; then
            classpath="${classpath}:${intermediary_jar}"
            log "Added intermediary mappings to classpath"
        else
            log "WARNING: Intermediary mappings JAR not found: $intermediary_jar"
        fi

        # Use production Minecraft client JAR (downloaded from Mojang)
        mc_jar="$RUNTIME_DIR/libs/minecraft-client-1.21.1.jar"
        if [ ! -f "$mc_jar" ]; then
            log_error "Production Minecraft JAR not found: $mc_jar"
            exit 3
        fi

        # Count loaded mods
        local mod_count=$(ls -1 "$RUN_DIR/mods/"*.jar 2>/dev/null | wc -l)
        log "Mods in mods/ folder: $mod_count"

        # Production mode JVM args (no development flag)
        jvm_args="${JAVA_OPTS:--Xmx4G -Xms2G}"
        jvm_args="$jvm_args -Dfabric.gameJarPath=$mc_jar"
        jvm_args="$jvm_args -Diconexporter.autoExportOnStartup=true"
        jvm_args="$jvm_args -Diconexporter.autoCreateWorld=true"
        jvm_args="$jvm_args -Diconexporter.quitAfterExport=true"

    else
        # DEVELOPMENT MODE: Load mods from classpath, use named mappings
        log "Running in DEVELOPMENT mode (named mappings)"

        # Build classpath from the development classpath file
        while IFS= read -r line || [[ -n "$line" ]]; do
            if [ -n "$line" ]; then
                classpath="${classpath}:${RUNTIME_DIR}/${line}"
            fi
        done < "${RUNTIME_DIR}/classpath.txt"
        classpath="${classpath:1}" # Remove leading colon

        # Add Linux native JARs to classpath
        for native_jar in "$RUNTIME_DIR/libs/"*-natives-${native_classifier}.jar; do
            if [ -f "$native_jar" ]; then
                classpath="${classpath}:${native_jar}"
            fi
        done

        # Use development Minecraft JAR (already in named namespace)
        mc_jar=$(find "$RUNTIME_DIR/libs" -name "minecraft-merged-*.jar" | head -1)
        if [ -z "$mc_jar" ] || [ ! -f "$mc_jar" ]; then
            log_error "Development Minecraft JAR not found in libs"
            exit 3
        fi

        # Generate remap classpath file for development mode
        local remap_file="$RUN_DIR/remap-classpath.txt"
        printf '%s' "" > "$remap_file"
        for jar in "$RUNTIME_DIR/libs/"cyclopscore*.jar "$RUNTIME_DIR/libs/"iconexporter*.jar; do
            if [ -f "$jar" ]; then
                echo "$jar" >> "$remap_file"
            fi
        done
        log "Remap classpath file contents:"
        cat "$remap_file" | while read line; do log "  - $line"; done

        # Development mode JVM args
        jvm_args="${JAVA_OPTS:--Xmx4G -Xms2G}"
        jvm_args="$jvm_args -Dfabric.development=true"
        jvm_args="$jvm_args -Dfabric.remapClasspathFile=$remap_file"
        jvm_args="$jvm_args -Dfabric.gameJarPath=$mc_jar"
        jvm_args="$jvm_args -Diconexporter.autoExportOnStartup=true"
        jvm_args="$jvm_args -Diconexporter.autoCreateWorld=true"
        jvm_args="$jvm_args -Diconexporter.quitAfterExport=true"
    fi

    log "Classpath entries: $(echo "$classpath" | tr ':' '\n' | wc -l)"
    log "Main class: $main_class"
    log "Minecraft JAR: $mc_jar"
    log "Run directory: $RUN_DIR"

    # Launch Minecraft
    java $jvm_args \
        -cp "$classpath" \
        "$main_class" \
        --gameDir "$RUN_DIR" \
        > "$EXPORTS_DIR/minecraft.log" 2>&1 &
    MC_PID=$!

    log "Minecraft started (PID: $MC_PID)"

    # Wait a moment for Minecraft to start loading
    sleep 10

    # Check if it's still running
    if ! kill -0 $MC_PID 2>/dev/null; then
        log_error "Minecraft failed to start - check minecraft.log"
        tail -100 "$EXPORTS_DIR/minecraft.log" | tee -a "$LOG_FILE"
        exit 3
    fi

    log "Minecraft is loading, exports will begin automatically..."
}

# ============================================================================
# Step 5: Monitor Export Progress
# ============================================================================

monitor_export() {
    log "Monitoring export progress..."

    local timeout=${ICONEXPORTER_TIMEOUT:-1800}  # 30 minutes default
    local elapsed=0
    local check_interval=5

    while [ $elapsed -lt $timeout ]; do
        # Check if Minecraft crashed
        if ! kill -0 $MC_PID 2>/dev/null; then
            log "Minecraft process ended"

            # Check for error marker
            if [ -f "$ERROR_MARKER" ]; then
                log_error "Export failed. Error details:"
                cat "$ERROR_MARKER" | tee -a "$LOG_FILE"
                exit 2
            fi

            # Check for completion marker
            if [ -f "$COMPLETION_MARKER" ]; then
                log "Export completed successfully"
                return 0
            fi

            # Check if any exports were created
            if [ -d "$EXPORTS_DIR" ] && [ "$(find "$EXPORTS_DIR" -name "*.png" 2>/dev/null | wc -l)" -gt 0 ]; then
                log "Export files found, assuming success"
                return 0
            fi

            log_error "Minecraft exited without completion marker"
            log "Last 50 lines of minecraft.log:"
            tail -50 "$EXPORTS_DIR/minecraft.log" | tee -a "$LOG_FILE"
            exit 3
        fi

        # Check for completion marker
        if [ -f "$COMPLETION_MARKER" ]; then
            log "Export completion marker found"

            # Give Minecraft a moment to fully write files
            sleep 3

            # Gracefully stop Minecraft
            log "Stopping Minecraft gracefully..."
            kill $MC_PID 2>/dev/null || true
            sleep 5
            kill -9 $MC_PID 2>/dev/null || true

            log "Export completed successfully"
            return 0
        fi

        # Check for error marker
        if [ -f "$ERROR_MARKER" ]; then
            log_error "Export error marker found"
            cat "$ERROR_MARKER" | tee -a "$LOG_FILE"
            kill $MC_PID 2>/dev/null || true
            exit 2
        fi

        # Log progress every 30 seconds
        if [ $((elapsed % 30)) -eq 0 ]; then
            log "Export in progress... (${elapsed}s / ${timeout}s)"

            # Show recent log activity
            if [ -f "$EXPORTS_DIR/minecraft.log" ]; then
                tail -3 "$EXPORTS_DIR/minecraft.log" 2>/dev/null | while read line; do
                    log "  MC: $line"
                done
            fi
        fi

        sleep $check_interval
        elapsed=$((elapsed + check_interval))
    done

    log_error "Export timeout after ${timeout}s"
    kill $MC_PID 2>/dev/null || true
    exit 4
}

# ============================================================================
# Step 6: Validate Exports
# ============================================================================

validate_exports() {
    log "Validating exports..."

    # Check for structured output directory
    local export_path="$EXPORTS_DIR/${ICONEXPORTER_MODPACK_NAME:-modpack}"

    if [ ! -d "$export_path" ]; then
        log "Note: Structured export directory not found: $export_path"
        log "Checking for any exports in $EXPORTS_DIR..."
    fi

    # Count exported files
    local icon_count=$(find "$EXPORTS_DIR" -name "*.png" 2>/dev/null | wc -l)
    local json_count=$(find "$EXPORTS_DIR" -name "*.json" 2>/dev/null | wc -l)

    log "Validation results:"
    log "  - PNG icons: $icon_count"
    log "  - JSON files: $json_count"

    if [ "$icon_count" -eq 0 ] && [ "$json_count" -eq 0 ]; then
        log_error "No files exported - this may indicate a problem"
        log "Check minecraft.log for errors"
        exit 6
    fi

    log "Export validation successful"
}

# ============================================================================
# Step 7: Fix Permissions
# ============================================================================

fix_permissions() {
    log "Fixing file permissions..."

    # Ensure files are readable
    chmod -R u+rw,go+r "$EXPORTS_DIR" 2>/dev/null || {
        log "Warning: Could not change permissions"
    }

    log "Permissions fixed"
}

# ============================================================================
# Main Execution
# ============================================================================

main() {
    log "======================================================================"
    log "IconExporter Docker Entrypoint v$SCRIPT_VERSION"
    log "======================================================================"
    log "Environment:"
    log "  - Mode: $([ "$PRODUCTION_MODE" = "true" ] && echo "PRODUCTION (user mods supported)" || echo "DEVELOPMENT (user mods NOT supported)")"
    log "  - Scale: ${ICONEXPORTER_SCALE:-32}"
    log "  - Modpack: ${ICONEXPORTER_MODPACK_NAME:-modpack}"
    log "  - Auto delay: ${ICONEXPORTER_AUTO_DELAY:-30}s"
    log "  - Timeout: ${ICONEXPORTER_TIMEOUT:-1800}s"
    log "  - Java opts: ${JAVA_OPTS:--Xmx4G -Xms2G}"
    log "  - Display: ${DISPLAY}"
    log "======================================================================"

    validate_environment
    configure_minecraft
    start_xvfb
    launch_minecraft
    monitor_export
    validate_exports
    fix_permissions

    log "======================================================================"
    log "Export process completed!"
    log "======================================================================"

    exit 0
}

main "$@"
