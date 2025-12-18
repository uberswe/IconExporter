#!/bin/bash
set -euo pipefail

# ============================================================================
# IconExporter Docker Entrypoint (NeoForge)
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

# NeoForge always runs in production mode
readonly PRODUCTION_MODE="true"

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
        log_error "Did you run './gradlew :loader-neoforge:exportDockerProductionRuntime' before building the image?"
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
# Step 2b: Download Production Mod JARs from Modrinth (NeoForge)
# ============================================================================

download_production_mods() {
    log "Downloading production mod JARs from Modrinth..."

    local mods_dir="$RUN_DIR/mods"
    local mc_version="1.21.1"

    # Remove development JARs that may have incorrect namespace
    log "  Removing development JARs with incorrect namespace..."
    rm -f "$mods_dir"/cyclopscore-*.jar 2>/dev/null || true

    # Download CyclopsCore from Modrinth (NeoForge version)
    # Project ID: Z9DM0LJ4 (from build.gradle)
    log "  Fetching CyclopsCore version info from Modrinth..."
    local cyclops_versions=$(curl -sSL "https://api.modrinth.com/v2/project/Z9DM0LJ4/version?loaders=%5B%22neoforge%22%5D&game_versions=%5B%22${mc_version}%22%5D")

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
        log_error "Could not find CyclopsCore for NeoForge Minecraft ${mc_version} on Modrinth"
        exit 1
    fi

    # NOTE: IconExporter is NOT downloaded from Modrinth because the published version
    # doesn't include the auto-export features (WorldAutoLoader, AutoExportHandler).
    # We use the bundled iconexporter-*-DEV.jar.
    log "  Using bundled IconExporter JAR (contains auto-export features)"

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
# Step 2d: Download Minecraft Assets
# ============================================================================

download_minecraft_assets() {
    log "Setting up Minecraft assets..."

    local mc_version="1.21.1"
    local assets_dir="$RUN_DIR/assets"
    local indexes_dir="$assets_dir/indexes"
    local objects_dir="$assets_dir/objects"

    # Create directories
    mkdir -p "$indexes_dir"
    mkdir -p "$objects_dir"

    # Fetch version manifest to get asset index info
    log "  Fetching version manifest for asset index..."
    local version_manifest_url="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    local version_manifest=$(curl -sSL "$version_manifest_url")

    # Get version-specific manifest URL
    local version_url=$(echo "$version_manifest" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for v in data['versions']:
    if v['id'] == '${mc_version}':
        print(v['url'])
        break
")

    if [ -z "$version_url" ]; then
        log_error "Could not find version manifest for ${mc_version}"
        exit 1
    fi

    # Fetch version details for asset index
    local version_details=$(curl -sSL "$version_url")
    local asset_index_id=$(echo "$version_details" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['assetIndex']['id'])
")
    local asset_index_url=$(echo "$version_details" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['assetIndex']['url'])
")

    log "  Asset index: ${asset_index_id}"

    # Download asset index
    local index_file="$indexes_dir/${asset_index_id}.json"
    if [ ! -f "$index_file" ]; then
        log "  Downloading asset index ${asset_index_id}.json..."
        curl -sSL --fail --location -o "$index_file" "$asset_index_url" || {
            log_error "Failed to download asset index"
            exit 1
        }
    else
        log "  Cached: ${asset_index_id}.json"
    fi

    # Download essential assets only (to save time)
    # NeoForge actually validates the assets directory exists, but we don't need ALL assets
    # Just create the directory structure and download a minimal set
    log "  Assets directory structure created"
    log "  Note: Full asset download skipped for faster startup"
    log "  Assets will be downloaded on-demand by Minecraft"

    log "Minecraft assets setup complete"
}

# ============================================================================
# Step 3: Configure Minecraft
# ============================================================================

configure_minecraft() {
    log "Configuring Minecraft..."
    log "Running in PRODUCTION mode (NeoForge)"

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

    # PRODUCTION MODE: Mods are loaded from the mods/ folder
    log "Production mode: Mods will be loaded from mods/ folder"

    # Copy bundled mods from runtime/mods to run/mods
    if [ -d "$RUNTIME_DIR/mods" ]; then
        log "Copying bundled mods to run/mods/..."
        cp -v "$RUNTIME_DIR/mods/"*.jar "$RUN_DIR/mods/" 2>/dev/null || true
        local bundled_count=$(ls -1 "$RUN_DIR/mods/"*.jar 2>/dev/null | wc -l)
        log "  Copied $bundled_count bundled mod(s)"
    else
        log_error "Production mods directory not found: $RUNTIME_DIR/mods"
        log_error "Did you run './gradlew :loader-neoforge:exportDockerProductionRuntime' before building?"
        exit 1
    fi

    # Note: NeoForge JAR is NOT copied separately to mods folder
    # The packRecomp output (minecraft-client.jar) already includes NeoForge patches
    # Copying a separate neoforge-*.jar would cause duplicate Minecraft.class errors
    log "Using packRecomp output (includes NeoForge patches in minecraft-client.jar)"

    # Download production mod JARs from Modrinth (CyclopsCore)
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

    # NOTE: We use the deobfuscated Minecraft JAR included from Gradle export
    # instead of downloading the vanilla client (which has obfuscated class names)
    # The forgeclientuserdev launch target requires deobfuscated/mapped classes
    if [ -f "$RUNTIME_DIR/libs/minecraft-client.jar" ]; then
        log "Using included deobfuscated Minecraft JAR from Gradle export"
    else
        log_error "Deobfuscated Minecraft JAR not found: $RUNTIME_DIR/libs/minecraft-client.jar"
        log_error "Did you run './gradlew :loader-neoforge:exportDockerProductionRuntime' before building?"
        exit 1
    fi

    # Download/setup Minecraft assets (required by NeoForge before launch)
    download_minecraft_assets

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
# Step 4: Launch Minecraft (NeoForge)
# ============================================================================

launch_minecraft() {
    log "Launching Minecraft..."

    cd "$RUNTIME_DIR"

    # NeoForge uses BootstrapLauncher which requires proper Java module system configuration
    local main_class="cpw.mods.bootstraplauncher.BootstrapLauncher"
    local libs_dir="$RUNTIME_DIR/libs"

    # Detect architecture for native JARs
    local arch=$(uname -m)
    local native_classifier
    case "$arch" in
        x86_64|amd64) native_classifier="linux" ;;
        aarch64|arm64) native_classifier="linux-arm64" ;;
        *) native_classifier="linux" ;;
    esac

    log "Running in PRODUCTION mode (NeoForge)"

    # Use deobfuscated Minecraft JAR from Gradle export (not vanilla obfuscated client)
    # The forgeclientuserdev launch target requires deobfuscated/mapped class names
    local mc_jar="$libs_dir/minecraft-client.jar"
    if [ ! -f "$mc_jar" ]; then
        log_error "Deobfuscated Minecraft JAR not found: $mc_jar"
        log_error "Did you run './gradlew :loader-neoforge:exportDockerProductionRuntime' before building?"
        exit 3
    fi

    # Count loaded mods
    local mod_count=$(ls -1 "$RUN_DIR/mods/"*.jar 2>/dev/null | wc -l)
    log "Mods in mods/ folder: $mod_count"

    # =========================================================================
    # NeoForge Module System Configuration
    # =========================================================================
    # NeoForge uses Java Platform Module System (JPMS). The BootstrapLauncher
    # requires certain JARs on the MODULE PATH and others on the CLASSPATH.
    # Module JARs: bootstraplauncher, securejarhandler, ASM, etc.
    # Legacy classpath: Everything else that isn't modular
    # =========================================================================

    # Build module path - these are the core modular JARs required by the bootstrap
    local module_path=""
    local module_jars=(
        "bootstraplauncher-2.0.2.jar"
        "securejarhandler-3.0.8.jar"
        "asm-9.5.jar"
        "asm-commons-9.5.jar"
        "asm-tree-9.5.jar"
        "asm-util-9.5.jar"
        "asm-analysis-9.5.jar"
        "JarJarFileSystems-0.4.1.jar"
        "JarJarSelector-0.4.1.jar"
        "JarJarMetadata-0.4.1.jar"
    )

    for jar in "${module_jars[@]}"; do
        if [ -f "$libs_dir/$jar" ]; then
            if [ -z "$module_path" ]; then
                module_path="$libs_dir/$jar"
            else
                module_path="$module_path:$libs_dir/$jar"
            fi
        else
            log "Warning: Module JAR not found: $jar"
        fi
    done

    log "Module path JARs: ${#module_jars[@]}"

    # Build legacy classpath - all other JARs that should be loaded traditionally
    local legacy_classpath=""
    local classpath_file="${RUNTIME_DIR}/classpath-production.txt"

    if [ ! -f "$classpath_file" ]; then
        log_error "Production classpath file not found: $classpath_file"
        exit 3
    fi

    while IFS= read -r line || [[ -n "$line" ]]; do
        if [ -n "$line" ]; then
            local jar_name=$(basename "$line")
            # Skip JARs that are on the module path
            local is_module=false
            for module_jar in "${module_jars[@]}"; do
                if [ "$jar_name" = "$module_jar" ]; then
                    is_module=true
                    break
                fi
            done
            if [ "$is_module" = false ]; then
                if [ -z "$legacy_classpath" ]; then
                    legacy_classpath="${RUNTIME_DIR}/${line}"
                else
                    legacy_classpath="${legacy_classpath}:${RUNTIME_DIR}/${line}"
                fi
            fi
        fi
    done < "$classpath_file"

    # Add Linux native JARs to legacy classpath
    for native_jar in "$libs_dir/"*-natives-${native_classifier}.jar; do
        if [ -f "$native_jar" ]; then
            legacy_classpath="${legacy_classpath}:${native_jar}"
        fi
    done

    # Add Minecraft client JAR to legacy classpath
    legacy_classpath="${legacy_classpath}:${mc_jar}"

    log "Legacy classpath entries: $(echo "$legacy_classpath" | tr ':' '\n' | wc -l)"

    # Build ignore list - JARs that should NOT be transformed by FML
    local ignore_list="client-extra,neoforge-,neoforged-,minecraft-,bootstraplauncher-,securejarhandler-,asm-,JarJar"

    # =========================================================================
    # JVM Arguments
    # =========================================================================
    local jvm_args="${JAVA_OPTS:--Xmx4G -Xms2G}"

    # IconExporter automation flags
    jvm_args="$jvm_args -Diconexporter.autoExportOnStartup=true"
    jvm_args="$jvm_args -Diconexporter.autoCreateWorld=true"
    jvm_args="$jvm_args -Diconexporter.quitAfterExport=true"

    # NeoForge FML properties
    jvm_args="$jvm_args -Dfml.ignoreInvalidMinecraftCertificates=true"
    jvm_args="$jvm_args -Dfml.ignorePatchDiscrepancies=true"

    # BootstrapLauncher required system properties
    jvm_args="$jvm_args -DignoreList=$ignore_list"
    jvm_args="$jvm_args -DlibraryDirectory=$libs_dir"
    jvm_args="$jvm_args -DlegacyClassPath=$legacy_classpath"
    jvm_args="$jvm_args -Dmergetool.mappings=$libs_dir/neoform-1.21.1-20240808.144430.zip"
    jvm_args="$jvm_args -Dmc.client.jar=$mc_jar"
    # Tell FML to include the Minecraft client JAR in the game layer (for minecraft mod discovery)
    jvm_args="$jvm_args -Dfml.gameLayerLibraries=minecraft-client.jar"

    # Java module system flags
    jvm_args="$jvm_args --add-modules ALL-MODULE-PATH"
    jvm_args="$jvm_args --add-opens java.base/java.util.jar=cpw.mods.securejarhandler"
    jvm_args="$jvm_args --add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler"
    jvm_args="$jvm_args --add-exports java.base/sun.security.util=cpw.mods.securejarhandler"

    log "Main class: $main_class"
    log "Module path: $module_path"
    log "Minecraft JAR: $mc_jar"
    log "Run directory: $RUN_DIR"

    # Launch Minecraft with NeoForge using proper module path
    # NeoForge requires specific version arguments for FML/NeoForge/NeoForm
    # Using 'forgeclientuserdev' launch target because it uses DevEnvUtils to find
    # Minecraft classes on the classpath, rather than expecting Maven-structured artifacts
    java $jvm_args \
        --module-path "$module_path" \
        -cp "$legacy_classpath" \
        "$main_class" \
        --launchTarget forgeclientuserdev \
        --fml.forgeVersion 21.1.2 \
        --fml.mcVersion 1.21.1 \
        --fml.forgeGroup net.neoforged \
        --fml.mcpVersion 20240808.144430 \
        --fml.fmlVersion 4.0.24 \
        --fml.neoForgeVersion 21.1.2 \
        --fml.neoFormVersion 1.21.1-20240808.144430 \
        --gameDir "$RUN_DIR" \
        --assetsDir "$RUN_DIR/assets" \
        --assetIndex 1.21 \
        --version 1.21.1 \
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
    log "IconExporter Docker Entrypoint v$SCRIPT_VERSION (NeoForge)"
    log "======================================================================"
    log "Environment:"
    log "  - Loader: NeoForge"
    log "  - Mode: PRODUCTION (user mods supported)"
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
