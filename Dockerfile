# IconExporter Docker Image
# Provides a headless environment for automated icon and data export from Minecraft modpacks
#
# Build approach:
# 1. Run ./gradlew :loader-fabric:exportDockerProductionRuntime locally to export runtime
# 2. Build Docker image (uses pre-exported runtime, no Gradle needed at container runtime)
# 3. Run container with mods mounted in /iconexporter/input-mods
#
# Production mode (default): Supports loading user mods from input-mods volume
# Development mode: For debugging, does not load user mods

FROM eclipse-temurin:21-jdk-jammy

LABEL maintainer="IconExporter"
LABEL description="Headless Minecraft icon and data exporter with Xvfb"

# Install required system packages for X11 virtual display and OpenGL
# python3 is needed for parsing Mojang version manifest JSON
RUN apt-get update && apt-get install -y \
    xvfb \
    x11-utils \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    libopenal1 \
    libxrender1 \
    libxext6 \
    libxi6 \
    libxrandr2 \
    libxxf86vm1 \
    libxfixes3 \
    libxcursor1 \
    libxinerama1 \
    ca-certificates \
    curl \
    python3 \
    && rm -rf /var/lib/apt/lists/*

# Create minecraft user (non-root for security)
RUN useradd -m -u 1000 minecraft

# Set up working directory
WORKDIR /iconexporter

# Copy pre-exported runtime (must run ./gradlew :loader-fabric:exportDockerRunScript first!)
COPY --chown=minecraft:minecraft docker-runtime/ /iconexporter/runtime/

# Copy entrypoint script
COPY --chown=minecraft:minecraft docker-entrypoint.sh /iconexporter/

# Set up directories and make scripts executable
RUN chmod +x /iconexporter/docker-entrypoint.sh /iconexporter/runtime/run-minecraft.sh \
    && mkdir -p /iconexporter/runtime/run/mods \
    && mkdir -p /iconexporter/runtime/run/config \
    && mkdir -p /iconexporter/exports \
    && chown -R minecraft:minecraft /iconexporter

# Switch to minecraft user
USER minecraft

# Environment variables with sensible defaults
ENV DISPLAY=:99
ENV ICONEXPORTER_SCALE=32
ENV ICONEXPORTER_MODPACK_NAME=""
ENV ICONEXPORTER_AUTO_DELAY=30
ENV ICONEXPORTER_TIMEOUT=1800
ENV ICONEXPORTER_PRODUCTION_MODE=true
ENV JAVA_OPTS="-Xmx4G -Xms2G"

# Volumes for input (mods) and output (exports)
VOLUME ["/iconexporter/input-mods", "/iconexporter/exports"]

# Set entrypoint
ENTRYPOINT ["/bin/bash", "/iconexporter/docker-entrypoint.sh"]

# Health check (optional - checks if Xvfb is running)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD pgrep Xvfb > /dev/null || exit 1
