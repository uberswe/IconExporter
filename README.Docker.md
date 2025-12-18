# IconExporter Docker Guide

This guide explains how to use IconExporter in a Docker container for fully automated icon and data export from Minecraft modpacks.

## Overview

The IconExporter Docker container provides a headless environment that:
- Automatically exports all item/block icons (rendered PNG files)
- Exports raw textures, models, recipes, and data
- Runs completely hands-free with zero manual intervention
- Works with Fabric, Forge, and NeoForge modpacks
- Uses Xvfb for virtual display rendering (required for icon screenshots)

## Quick Start

### Prerequisites

- Docker installed and running
- A modpack with mod JAR files (Fabric or NeoForge for 1.21.1)

**Note**: IconExporter is built and included automatically during Docker build - you don't need to add it to your mods!

**Available Docker Images:**
- `iconexporter:fabric` - For Fabric modpacks (default Dockerfile)
- `iconexporter:neoforge` - For NeoForge modpacks (Dockerfile.neoforge)

### Basic Usage

```bash
# 1. Build the Docker image (this takes a while - downloads Minecraft, builds mod)
cd /path/to/IconExporter
docker build -t iconexporter:fabric .

# 2. Prepare your mods directory
mkdir -p mods exports
cp /path/to/your/modpack/mods/*.jar mods/

# 3. Run the export
docker run --rm \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  -e ICONEXPORTER_MODPACK_NAME=mymodpack \
  iconexporter:fabric

# 4. Check the results
ls exports/mymodpack/*/icons/
```

## How It Works

The Docker image uses Gradle's Fabric Loom to properly set up and run Minecraft:

1. **Build phase**: Compiles IconExporter mod and downloads all Minecraft dependencies via Loom
2. **Runtime phase**: Uses Xvfb for virtual display, runs Minecraft via `./gradlew runClient`
3. **Auto-export**: IconExporter automatically creates a world and exports everything
4. **Completion**: Container exits after export completes

This approach ensures all native libraries (LWJGL, OpenGL, etc.) are properly configured.

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ICONEXPORTER_SCALE` | `32` | Icon size in pixels (16, 32, 64, 128, etc.) |
| `ICONEXPORTER_MODPACK_NAME` | `modpack` | Modpack identifier (used in export path) |
| `ICONEXPORTER_AUTO_DELAY` | `30` | Seconds to wait before export (allows mods to load) |
| `ICONEXPORTER_TIMEOUT` | `1800` | Maximum seconds for export (30 minutes) |
| `JAVA_OPTS` | `-Xmx4G -Xms2G` | JVM memory settings |

### Examples

**High-resolution icons:**
```bash
docker run --rm \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  -e ICONEXPORTER_SCALE=128 \
  iconexporter:fabric
```

**Large modpack with more memory:**
```bash
docker run --rm \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  -e JAVA_OPTS="-Xmx8G -Xms4G" \
  -e ICONEXPORTER_TIMEOUT=3600 \
  iconexporter:fabric
```

## Using Docker Compose

The project includes a `docker-compose.yml` file for easier configuration:

```bash
# Place mods in ./mods directory, then run:
ICONEXPORTER_SCALE=64 ICONEXPORTER_MODPACK_NAME=atm10 docker-compose up
```

## Output Structure

When `useStructuredOutput=true` (default in Docker), exports are organized as:

```
exports/
└── {modpack_name}/
    └── {mc_version}/
        └── {loader}/
            ├── icons/           # Rendered item/block icons
            │   ├── minecraft__stone.png
            │   └── ...
            ├── textures/
            │   ├── items/       # Raw item textures
            │   └── blocks/      # Raw block textures
            ├── models/
            │   ├── items/       # Item model JSONs
            │   └── blocks/      # Block model JSONs
            ├── recipes/         # All recipes (JSON)
            ├── data/
            │   ├── items/       # Item metadata (JSON)
            │   └── blocks/      # Block metadata (JSON)
            ├── translations/
            │   ├── items.json
            │   └── blocks.json
            └── meta/
                ├── modpack.json
                ├── mods.json
                └── datapacks.json
```

## Exit Codes

The container exits with specific codes to indicate status:

| Code | Meaning |
|------|---------|
| 0 | Success - all exports completed |
| 1 | Validation failure (no mods found, missing IconExporter, etc.) |
| 2 | Export error (check `.export_error` file in exports directory) |
| 3 | Minecraft crashed unexpectedly |
| 4 | Export timeout exceeded |
| 5 | Export directory not found after completion |
| 6 | No files exported (validation failure) |

## Monitoring Progress

### Log Files

The container writes logs to `exports/docker-export.log`:

```bash
# Follow logs during export
tail -f exports/docker-export.log
```

### Completion Markers

The entrypoint script monitors for marker files:

- `.export_complete` - Written when export succeeds
- `.export_error` - Written when export fails (contains error details)

## Troubleshooting

### Common Issues

**1. Out of memory**
```
java.lang.OutOfMemoryError: Java heap space
```
**Solution**: Increase memory with `-e JAVA_OPTS="-Xmx8G -Xms4G"`.

**2. Export timeout**
```
ERROR: Export timeout after 1800s
```
**Solution**: Increase timeout with `-e ICONEXPORTER_TIMEOUT=3600` (1 hour).

**3. OpenGL/rendering errors**
```
GLFW error...
```
**Solution**: Ensure Xvfb is running. Check logs in `exports/docker-export.log`.

### Debug Mode

Run the container with bash to debug:

```bash
docker run --rm -it \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  --entrypoint /bin/bash \
  iconexporter:fabric

# Inside container:
# Check environment
env | grep ICONEXPORTER

# Manually start Xvfb
Xvfb :99 -screen 0 1280x720x24 +extension GLX &

# Check display
DISPLAY=:99 xdpyinfo

# Run Minecraft manually
./gradlew :loader-fabric:runClient --no-daemon
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Export Icons

on:
  workflow_dispatch:
    inputs:
      modpack:
        description: 'Modpack name'
        required: true

jobs:
  export:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Download mods
        run: |
          mkdir -p mods exports
          # Your mod download logic here

      - name: Build Docker image
        run: docker build -t iconexporter:ci .

      - name: Run export
        run: |
          docker run --rm \
            -v $(pwd)/mods:/iconexporter/input-mods:ro \
            -v $(pwd)/exports:/iconexporter/exports \
            -e ICONEXPORTER_MODPACK_NAME=${{ github.event.inputs.modpack }} \
            iconexporter:ci

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: exported-icons
          path: exports/
```

## Performance Tips

1. **Memory**: Allocate sufficient heap for large modpacks
   - Small (< 100 mods): 2-4GB
   - Medium (100-500 mods): 4-6GB
   - Large (500+ mods): 6-8GB

2. **Timeout**: Adjust based on mod count
   - ~100 mods: 30 minutes (default)
   - ~500 mods: 1 hour
   - ~1000 mods: 2 hours

3. **Scale**: Lower resolution exports faster
   - 16px: Fastest, smallest files
   - 32px: Good balance (default)
   - 64px+: High quality, slower

## Loader-Specific Images

### Fabric (Default)

The default Dockerfile builds a Fabric image:

```bash
# Build Fabric image
docker build -t iconexporter:fabric .

# Run with Fabric mods
docker run --rm \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  iconexporter:fabric
```

### NeoForge

A dedicated NeoForge Dockerfile and entrypoint are provided:

```bash
# Build NeoForge image
docker build -f Dockerfile.neoforge -t iconexporter:neoforge .

# Run with NeoForge mods
docker run --rm \
  -v $(pwd)/mods:/iconexporter/input-mods:ro \
  -v $(pwd)/exports:/iconexporter/exports \
  iconexporter:neoforge
```

**NeoForge Technical Details:**
- Uses pre-built production runtime (faster startup than Gradle-based approach)
- Includes deobfuscated Minecraft classes via NeoForm `packRecomp` task
- Downloads CyclopsCore from Modrinth automatically
- Supports userdev launch target for development-friendly runtime

**Build the NeoForge runtime (maintainers only):**
```bash
# This exports JARs for the Docker production runtime
./gradlew :loader-neoforge:exportDockerProductionRuntime
```

### Forge (Manual Setup)

For Forge, modify the Dockerfile manually:

```dockerfile
# In Dockerfile, change Gradle commands:
RUN ./gradlew :loader-forge:build --no-daemon -x test
# ... and in entrypoint:
./gradlew :loader-forge:runClient --no-daemon
```

## Support

- **Issues**: https://github.com/CyclopsMC/IconExporter/issues
- **Discussions**: Check the repository discussions
- **Documentation**: See main README.md for mod usage

## License

MIT License - see LICENSE file for details
