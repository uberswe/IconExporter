# Running NeoForge/Forge Mods Without Full Minecraft Launch

Client-side rendering in Minecraft **requires an OpenGL context**, making true headless operation challenging—but several practical solutions exist. The most reliable approach combines **Xvfb (X Virtual Framebuffer)** with automated client launches, while the **mc-runtime-test** GitHub Action provides CI/CD integration. Item rendering can work without a loaded world since `ItemRenderer` accepts null world parameters, but you still need the Minecraft client initialized for texture atlases and baked models.

---

## Xvfb provides the most reliable headless rendering

For Linux environments (including CI/CD), **Xvfb** creates a virtual display that satisfies LWJGL/OpenGL's requirement for a windowing system while producing no visible output. This is the approach used by major CI/CD tools like mc-runtime-test.

```bash
# Installation
sudo apt-get install -y xvfb

# Running Minecraft with Xvfb
xvfb-run -e /dev/stdout -s "-screen 0 1920x1080x24 -ac" ./gradlew runClient

# Or manually manage the display
Xvfb :99 -screen 0 1920x1080x24 &
export DISPLAY=:99
./gradlew runClient
```

For Docker containerization:
```dockerfile
FROM eclipse-temurin:21-jdk
RUN apt-get update && apt-get install -y xvfb libgl1-mesa-glx
ENV DISPLAY=:99
CMD ["sh", "-c", "Xvfb :99 -screen 0 1920x1080x24 & java -jar minecraft.jar"]
```

**HeadlessMC** offers an alternative by patching LWJGL functions to return stub values, but this **breaks actual rendering**—LWJGL calls return null or empty values, making icon export impossible. Use HeadlessMC only for non-rendering automation tasks.

---

## NeoForge testing frameworks lack client rendering support

NeoForge provides **JUnit integration** and **GameTest framework**, but neither supports client-side rendering:

| Framework | Registry Access | Server Context | Client Rendering |
|-----------|----------------|----------------|------------------|
| JUnit Unit Tests | ✅ | ✅ (with EphemeralTestServerProvider) | ❌ |
| GameTestServer | ✅ | ✅ | ❌ |
| Datagen | ✅ | ❌ | ❌ |

**JUnit configuration** (build.gradle with ModDevGradle):
```groovy
neoForge {
    unitTest {
        enable()
        testedMod = mods.yourmod
    }
}

dependencies {
    testImplementation "net.neoforged:testframework:${neoforge_version}"
}
```

```java
@ExtendWith(EphemeralTestServerProvider.class)
public class ItemRegistryTest {
    @Test
    void testItemsRegistered(MinecraftServer server) {
        // Full registry access—no client rendering
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("yourmod:youritem"));
        assertNotNull(item);
    }
}
```

The test environment runs server-side only—`Minecraft.getInstance()` and OpenGL operations are unavailable.

---

## Datagen generates JSON but not rendered assets

**Data generation** runs during build without a full game launch and can export recipes, loot tables, and metadata—but not rendered icons:

```java
@SubscribeEvent
public static void gatherData(GatherDataEvent.Client event) {
    // Export all non-rendered item data
    event.createProvider(ItemMetadataProvider::new);
}

public class ItemMetadataProvider implements DataProvider {
    private final PackOutput output;
    
    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        JsonArray items = new JsonArray();
        for (Item item : BuiltInRegistries.ITEM) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
            obj.addProperty("maxStackSize", item.getDefaultMaxStackSize());
            obj.addProperty("rarity", item.getRarity(item.getDefaultInstance()).name());
            items.add(obj);
        }
        Path path = output.getOutputFolder().resolve("custom/items.json");
        return DataProvider.saveStable(cache, items, path);
    }
}
```

Run with `./gradlew runClientData` (1.21.4+) or `./gradlew runData` (earlier versions).

---

## Item rendering works without a world context

Critically, **`ItemRenderer.renderStatic()` accepts a null world parameter**. Minecraft renders inventory items without world context daily—you can leverage this for icon export:

```java
@OnlyIn(Dist.CLIENT)
public class IconExporter {
    public static void exportAllIcons(int size) {
        Minecraft mc = Minecraft.getInstance();
        
        // Create offscreen render target
        RenderTarget target = new RenderTarget(size, size, true);
        target.setClearColor(0, 0, 0, 0); // Transparent background
        
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            
            PoseStack poseStack = new PoseStack();
            MultiBufferSource.BufferSource bufferSource = 
                mc.renderBuffers().bufferSource();
            
            // NULL WORLD IS VALID HERE
            mc.getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GUI,
                15728880,  // Full brightness (240 << 4 | 240 << 20)
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                null,      // @Nullable Level - works without world!
                0
            );
            
            bufferSource.endBatch();
            target.unbindWrite();
            
            // Save to file
            saveRenderTarget(target, getOutputPath(item));
        }
    }
    
    private static void saveRenderTarget(RenderTarget target, Path path) {
        NativeImage image = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY();
        image.writeToFile(path);
        image.close();
    }
}
```

For blocks requiring tint colors, implement a minimal `BlockAndTintGetter`:

```java
public class MinimalBlockContext implements BlockAndTintGetter {
    public static final MinimalBlockContext INSTANCE = new MinimalBlockContext();
    
    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }
    
    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultBlockState();
    }
    
    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return 0x7CBD6B; // Default grass green
    }
    
    @Override
    public float getShade(Direction direction, boolean shade) {
        return shade ? switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        } : 1.0f;
    }
    
    @Override
    public LevelLightEngine getLightEngine() { return null; }
    @Override
    public int getHeight() { return 256; }
    @Override
    public int getMinY() { return -64; }
}
```

---

## JEI and REI render icons on-demand, not pre-cached

Both **JEI** and **REI** render thousands of icons dynamically rather than pre-generating them:

- **Batch rendering**: `IngredientListBatchRenderer` groups items for efficient GPU calls
- **Viewport culling**: Only items visible on screen are rendered
- **Fast renderer**: `ItemStackFastRenderer` bypasses some vanilla overhead

JEI's rendering stack:
```
IngredientListOverlay.drawScreen()
  └─ IngredientGrid.draw()
      └─ IngredientListBatchRenderer.render()
          └─ ItemStackFastRenderer.renderItemAndEffectIntoGUI()
```

This approach prioritizes runtime performance over pre-computation—different from batch export use cases.

---

## mc-runtime-test enables automated CI/CD pipelines

The **mc-runtime-test** GitHub Action combines HeadlessMC, Xvfb, and automated world loading for CI/CD:

```yaml
name: Export Icons
on: [workflow_dispatch]

jobs:
  export:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
      
      - name: Build mod
        run: ./gradlew build
      
      - name: Stage mods
        run: |
          mkdir -p run/mods
          cp build/libs/your-mod.jar run/mods
          # Add IconExporter or your exporter mod
          wget -O run/mods/iconexporter.jar "https://modrinth.com/mod/icon-exporter/..."
      
      - name: Run export
        uses: headlesshq/mc-runtime-test@4.1.0
        with:
          mc: 1.21.1
          modloader: neoforge
          mc-runtime-test: neoforge
          java: 21
          xvfb: true
          # Custom JVM args for automated export
          headlessmc-command: '--jvm "-Dautoexport=true"'
      
      - name: Upload icons
        uses: actions/upload-artifact@v4
        with:
          name: exported-icons
          path: run/icon-exports-x64/
```

For custom automation, modify your mod to detect a system property and auto-export:

```java
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AutoExporter {
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (System.getProperty("autoexport") != null && 
            event.getLevel().isClientSide()) {
            
            // Delay to ensure resources loaded
            Minecraft.getInstance().tell(() -> {
                IconExporter.exportAllIcons(64);
                Minecraft.getInstance().stop();
            });
        }
    }
}
```

---

## IconExporter by CyclopsMC handles production icon export

**IconExporter** (https://github.com/CyclopsMC/IconExporter) is the most actively maintained solution:

- Supports NeoForge, Forge, Fabric (1.18.2–1.21.4)
- Command: `/iconexporter export [size]`
- Exports transparent PNGs for all registered items
- Metadata export: `/iconexporter exportmetadata`

Output format: `<modid>__<itemid>__<metadata>[__<NBT>].png`

For automated use, combine with mc-runtime-test or add auto-trigger logic.

---

## Practical recommendations by use case

**For CI/CD icon export:**
1. Use **mc-runtime-test** with `xvfb: true`
2. Include IconExporter or custom export mod
3. Add auto-trigger system property check
4. Upload exported assets as artifacts

**For local development:**
```bash
# Linux/WSL
xvfb-run ./gradlew runClient
# Then run export command in-game
```

**For data-only export (no icons):**
- Use datagen with custom `DataProvider`
- No client launch needed
- Run with `./gradlew runClientData`

**For minimal world context:**
- Implement `BlockAndTintGetter` stub
- Use `EmptyBlockGetter.INSTANCE` for air-only scenarios
- Pass to block rendering methods that require world

The fundamental constraint is that OpenGL requires a window system—Xvfb satisfies this on Linux while HeadlessMC's LWJGL stubbing breaks actual rendering. For production icon export with client rendering, automated Xvfb launches remain the most reliable approach.