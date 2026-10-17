package com.yutadigital.herobrine.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yutadigital.herobrine.core.Ability;
import com.yutadigital.herobrine.core.PlayerSettings;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import com.yutadigital.herobrine.server.HerobrineServer;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

/** Test-only real-client checks, compiled only with -Pclient_tests. Never included in release jars. */
public final class ClientSmokeTest {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final ArrayDeque<Step> steps = new ArrayDeque<>();
    private static final List<String> results = new ArrayList<>();
    private static Path output;
    private static Step current;
    private static int ticks, startedTick, captureFrames;
    private static boolean started, finished, failed;
    private static boolean capturedFlash;
    private static boolean failureCapture;
    private static volatile boolean serverCheckComplete;
    private static volatile String serverCheckFailure;
    private static Properties lifecycleExpected;
    private static net.minecraft.client.player.LocalPlayer previousPlayer;
    private static double returnX, returnY, returnZ;
    private static java.util.UUID lifecycleUuid;
    private static String capture;
    private static Properties previous;
    private static boolean previousGlow;
    private static int previousScale, previousWidth, previousHeight;
    private static CameraType previousCamera;
    private static KeyMapping openKey;
    private static InputConstants.Key previousOpenKey;

    private ClientSmokeTest() { }
    public static boolean finished() { return finished; }
    public static boolean failed() { return failed; }

    public static void start() {
        if (started) return;
        started = true;
        output = Path.of(System.getProperty("herobrineultimate.testOut", "client-test-results"));
        try {
            Files.createDirectories(output);
            check(MC.player != null && MC.level != null && MC.getConnection() != null, "Real client and world connected");
            check(HerobrineClient.hasState() && HerobrineClient.allowed(), "Server acknowledged owner permission");
            check(MC.getSingleplayerServer() != null && !MC.getSingleplayerServer().getWorldData().getAllowCommands(),
                    "Abilities available in a real singleplayer world with cheats disabled");
            previous = HerobrineClient.settings().toProperties();
            previousGlow = HerobrineClient.panelGlow();
            previousScale = MC.options.guiScale().get();
            previousWidth = MC.getWindow().getWidth();
            previousHeight = MC.getWindow().getHeight();
            previousCamera = MC.options.getCameraType();
            for (KeyMapping mapping : MC.options.keyMappings) {
                if (mapping.getName().equals("key.herobrineultimate.open")) openKey = mapping;
            }
            check(openKey != null, "Registered GUI shortcut");
            // saveString is loader independent, unlike Forge's getKey() extension.
            previousOpenKey = InputConstants.getKey(openKey.saveString());
            step("Server permits non-OP player with cheats disabled", () -> {
                var server = MC.getSingleplayerServer();
                var uuid = MC.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) serverCheckFailure = "Real server player missing";
                    else if (player.hasPermissions(2)) serverCheckFailure = "Fixture unexpectedly has OP permission";
                    else if (!HerobrineServer.allowed(player)) serverCheckFailure = "Non-OP player was denied";
                    serverCheckComplete = true;
                });
            }, () -> {
                if (serverCheckFailure != null) throw new AssertionError(serverCheckFailure);
                return serverCheckComplete;
            });
            step("Open panel through registered shortcut handler", () -> {
                MC.setScreen(null);
                HerobrineClient.open();
            }, () -> MC.screen instanceof HerobrineScreen);
            step("Master active", () -> { if (!HerobrineClient.settings().master) HerobrineClient.master(); },
                    () -> HerobrineClient.settings().master);
            toggleStep(Ability.EFFECTS, true);
            toggleStep(Ability.HEROBRINE, true);
            step("Transformed skin and standard model mixins", () -> {
                check(MC.player.getSkinTextureLocation().equals(HerobrineClient.SKIN), "Transformed skin hook");
                check(MC.player.getModelName().equals("default"), "Standard model hook");
                var info = MC.getConnection().getPlayerInfo(MC.player.getUUID());
                check(info != null && MC.gui.getTabList().getNameForDisplay(info).getString().equals("Herobrine"),
                        "TAB name hook");
                try {
                    Field layers = LivingEntityRenderer.class.getDeclaredField("layers");
                    layers.setAccessible(true);
                    Object renderer = MC.getEntityRenderDispatcher().getRenderer(MC.player);
                    List<?> actual = (List<?>) layers.get(renderer);
                    check(actual.stream().anyMatch(layer -> layer instanceof HerobrineEyesLayer), "Emissive eyes layer injected");
                } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                check(!MC.player.getGameProfile().getName().equals("Herobrine"), "Account profile name preserved");
                check(capturedFlash, "Optional transformation flash reached the real framebuffer");
            }, () -> true);
            screenshotStep("transformed-panel");
            step("First person transformed rendering", () -> {
                MC.setScreen(null);
                MC.options.setCameraType(CameraType.FIRST_PERSON);
            }, () -> MC.screen == null);
            screenshotStep("transformed-first-person");
            step("Third person transformed rendering", () -> MC.options.setCameraType(CameraType.THIRD_PERSON_FRONT), () -> true);
            screenshotStep("transformed-third-person");
            step("Return to panel", () -> MC.setScreen(new HerobrineScreen()), () -> MC.screen instanceof HerobrineScreen);
            for (Ability ability : Ability.values()) toggleStep(ability, true);
            step("Master off through real footer widget", () -> click(findMessage("herobrineultimate.ui.master_on")),
                    () -> !HerobrineClient.settings().master);
            step("Master preserves every individual selection", () -> {
                for (Ability ability : Ability.values()) check(HerobrineClient.settings().selected(ability), "Retained " + ability);
            }, () -> true);
            step("Master on through real footer widget", () -> click(findMessage("herobrineultimate.ui.master_off")),
                    () -> HerobrineClient.settings().master);
            toggleStep(Ability.INVISIBILITY, false);
            for (String key : new String[]{"damage", "move", "mine", "attack"}) {
                Ability category = key.equals("damage") ? Ability.COMBAT : Ability.MOTION;
                step("Slider " + key + " maximum via mouse", () -> {
                    select(category);
                    AbstractWidget slider = slider(key);
                    reveal(slider);
                    clickAt(slider, slider.getX() + slider.getWidth() - 1);
                }, () -> HerobrineClient.settings().multiplier(key) == PlayerSettings.maximum(key));
                step("Slider " + key + " minimum via mouse", () -> {
                    AbstractWidget slider = slider(key);
                    reveal(slider);
                    clickAt(slider, slider.getX() + 1);
                }, () -> HerobrineClient.settings().multiplier(key) == 1);
            }
            step("Slider keyboard adjustment", () -> {
                select(Ability.COMBAT);
                AbstractWidget slider = slider("damage");
                reveal(slider);
                if (!(Boolean) field(slider, "canChangeValue")) panel().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                check(panel().keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0), "Keyboard slider accepted");
            }, () -> HerobrineClient.settings().multiplier("damage") > 1);
            step("Slider drag release outside viewport commits", () -> {
                AbstractWidget slider = slider("damage");
                reveal(slider);
                HerobrineScreen screen = panel();
                double y = slider.getY() + slider.getHeight() / 2.0;
                check(screen.mouseClicked(slider.getX() + 1, y, 0), "Slider drag starts");
                check(screen.mouseDragged(screen.width + 80, y, 0, screen.width, 0), "Slider drag captured");
                screen.mouseReleased(screen.width + 80, screen.height + 80, 0);
            }, () -> HerobrineClient.settings().multiplier("damage") == PlayerSettings.maximum("damage"));
            step("Local glow independent of abilities", () -> {
                HerobrineClient.togglePanelGlow();
                check(HerobrineClient.panelGlow() != previousGlow, "Local preference toggled");
                Path preferences = MC.gameDirectory.toPath().resolve("config/herobrineultimate-client.properties");
                check(Files.isRegularFile(preferences), "Local preference automatically written");
                try (var stream = Files.newInputStream(preferences)) {
                    Properties saved = new Properties();
                    saved.load(stream);
                    check(Boolean.parseBoolean(saved.getProperty("panelGlow")) == HerobrineClient.panelGlow(),
                            "Saved glow value matches local state");
                } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
            }, () -> true);
            step("Open native key binding settings", () -> click(findLiteral("K")), () -> MC.screen instanceof KeyBindsScreen);
            step("Return from native settings", () -> MC.screen.onClose(), () -> MC.screen instanceof HerobrineScreen);
            step("Rebound open shortcut closes panel", () -> {
                openKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F12));
                KeyMapping.resetMapping();
                check(panel().keyPressed(GLFW.GLFW_KEY_F12, 0, 0), "Rebound key handled");
            }, () -> MC.screen == null);
            step("Mouse-rebound shortcut closes panel", () -> {
                HerobrineClient.open();
                openKey.setKey(InputConstants.Type.MOUSE.getOrCreate(2));
                KeyMapping.resetMapping();
                check(panel().mouseClicked(1, 1, 2), "Mouse-rebound key handled");
            }, () -> MC.screen == null);
            step("Restore shortcut and open", () -> {
                openKey.setKey(previousOpenKey);
                KeyMapping.resetMapping();
                HerobrineClient.open();
            }, () -> MC.screen instanceof HerobrineScreen);
            for (int[] size : new int[][]{{320,240,1}, {854,480,1}, {1280,720,2}, {1280,720,3}}) {
                String label = size[0] + "x" + size[1] + "-scale" + size[2];
                step("Resize " + label, () -> {
                    MC.options.guiScale().set(size[2]);
                    MC.getWindow().setWindowed(size[0], size[1]);
                    MC.resizeDisplay();
                    MC.setScreen(new HerobrineScreen());
                }, () -> MC.getWindow().getWidth() == size[0] && MC.getWindow().getHeight() == size[1]);
                step("Viewport and navigation " + label, () -> validateLayoutAndNavigation(), () -> true);
                screenshotStep("panel-" + label);
            }
            step("Real death retains settings", () -> {
                lifecycleExpected = HerobrineClient.settings().toProperties();
                lifecycleUuid = MC.player.getUUID();
                previousPlayer = MC.player;
                MC.setScreen(null);
                var server = MC.getSingleplayerServer();
                server.execute(() -> server.getPlayerList().getPlayer(lifecycleUuid).kill());
            }, () -> MC.player != null && MC.player.isDeadOrDying());
            screenshotStep("death-before-respawn");
            step("Vanilla respawn preserves all ability settings", () -> MC.player.respawn(),
                    () -> MC.player != null && !MC.player.isDeadOrDying() && MC.player != previousPlayer
                            && HerobrineClient.hasState() && lifecycleExpected.equals(HerobrineClient.settings().toProperties())
                            && HerobrineClient.transformed(lifecycleUuid));
            step("Travel to Nether with a real dimension packet", () -> {
                returnX = MC.player.getX(); returnY = MC.player.getY(); returnZ = MC.player.getZ();
                var server = MC.getSingleplayerServer();
                server.execute(() -> {
                    var target = server.getLevel(Level.NETHER);
                    target.setBlockAndUpdate(new BlockPos(0, 120, 0), Blocks.STONE.defaultBlockState());
                    server.getPlayerList().getPlayer(lifecycleUuid).teleportTo(target, .5, 121, .5, 0, 0);
                });
            }, () -> MC.level != null && MC.level.dimension().equals(Level.NETHER)
                    && lifecycleExpected.equals(HerobrineClient.settings().toProperties()));
            screenshotStep("nether-dimension");
            step("Return to Overworld with settings intact", () -> {
                var server = MC.getSingleplayerServer();
                server.execute(() -> server.getPlayerList().getPlayer(lifecycleUuid)
                        .teleportTo(server.getLevel(Level.OVERWORLD), returnX, returnY, returnZ, 0, 0));
            }, () -> MC.level != null && MC.level.dimension().equals(Level.OVERWORLD)
                    && lifecycleExpected.equals(HerobrineClient.settings().toProperties()));
            step("Disconnect and stop the real integrated server", () -> {
                MC.level.disconnect();
                MC.clearLevel(new TitleScreen());
            },
                    () -> MC.player == null && MC.getSingleplayerServer() == null && !HerobrineClient.hasState());
            step("Reopen saved world and restore persisted settings", () -> {
                String world = System.getProperty("herobrineultimate.testWorld", "HerobrineUltimate-ClientTest");
                MC.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
            }, () -> MC.player != null && MC.level != null && HerobrineClient.hasState()
                    && MC.player.getUUID().equals(lifecycleUuid)
                    && lifecycleExpected.equals(HerobrineClient.settings().toProperties())
                    && HerobrineClient.transformed(lifecycleUuid));
            step("Open panel after world reload", () -> MC.setScreen(new HerobrineScreen()), () -> MC.screen instanceof HerobrineScreen);
            screenshotStep("panel-after-world-reload");
            step("Restore transformation through real UI", () -> { select(Ability.HEROBRINE); toggleTo(Ability.HEROBRINE, false); },
                    () -> !HerobrineClient.transformed(MC.player.getUUID()));
            step("Original skin restored", () -> check(!MC.player.getSkinTextureLocation().equals(HerobrineClient.SKIN), "Original skin restored"), () -> true);
            // Roll the settings back through the same real packet transport after checking all controls.
            for (Ability ability : Ability.values()) toggleStep(ability, Boolean.parseBoolean(previous.getProperty(ability.name())));
            for (String key : new String[]{"move", "mine", "attack", "damage"}) {
                double value = Double.parseDouble(previous.getProperty(key));
                step("Restore multiplier " + key, () -> HerobrineClient.number(key, value),
                        () -> HerobrineClient.settings().multiplier(key) == value);
            }
            step("Restore local presentation", () -> {
                if (HerobrineClient.panelGlow() != previousGlow) HerobrineClient.togglePanelGlow();
                MC.options.setCameraType(previousCamera);
                MC.options.guiScale().set(previousScale);
                MC.getWindow().setWindowed(previousWidth, previousHeight);
                MC.resizeDisplay();
                if (HerobrineClient.settings().master != Boolean.parseBoolean(previous.getProperty("master"))) HerobrineClient.master();
                MC.setScreen(null);
            }, () -> HerobrineClient.settings().master == Boolean.parseBoolean(previous.getProperty("master")));
        } catch (Throwable ex) { fail("initialization", ex); }
    }

    private static void toggleStep(Ability ability, boolean value) {
        step("GUI toggle " + ability + "=" + value, () -> {
            if (!(MC.screen instanceof HerobrineScreen)) MC.setScreen(new HerobrineScreen());
            select(ability);
            toggleTo(ability, value);
        }, () -> HerobrineClient.settings().selected(ability) == value);
    }
    private static void toggleTo(Ability ability, boolean value) {
        if (HerobrineClient.settings().selected(ability) != value) click(findMessage("herobrineultimate.ui.ability_toggle"));
    }
    private static void step(String name, Runnable action, BooleanSupplier done) { steps.add(new Step(name, action, done)); }
    private static void screenshotStep(String name) {
        step("Screenshot " + name, () -> { capture = name; captureFrames = 0; }, () -> capture == null);
    }
    public static void tick() {
        if (!started || finished || failureCapture) return;
        ticks++;
        try {
            if (current == null) {
                if (steps.isEmpty()) { finish(); return; }
                current = steps.removeFirst();
                startedTick = ticks;
                current.action.run();
            }
            if (ticks - startedTick >= 5 && current.done.getAsBoolean()) {
                results.add("{\"name\":" + quote(current.name) + ",\"status\":\"passed\"}");
                System.out.println("CLIENT_SMOKE PASS " + current.name);
                current = null;
            } else if (ticks - startedTick > 1200) throw new IllegalStateException("Timed out waiting for real client/server acknowledgement");
        } catch (Throwable ex) { fail(current == null ? "step" : current.name, ex); }
    }
    public static void renderTick() {
        if (failureCapture && !finished) {
            try (var image = Screenshot.takeScreenshot(MC.getMainRenderTarget())) { image.writeToFile(output.resolve("failure.png")); }
            catch (Throwable ex) { ex.printStackTrace(); }
            failureCapture = false;
            finish();
            return;
        }
        if (started && !finished && !capturedFlash && HerobrineClient.flashAlpha() > 0) {
            try (var image = Screenshot.takeScreenshot(MC.getMainRenderTarget())) {
                image.writeToFile(output.resolve("transformation-flash.png"));
                capturedFlash = true;
            } catch (Throwable ex) { fail("transformation flash screenshot", ex); }
        }
        if (capture == null || finished || ++captureFrames < 3) return;
        try (var image = Screenshot.takeScreenshot(MC.getMainRenderTarget())) {
            Path file = output.resolve(capture + ".png");
            image.writeToFile(file);
            check(Files.size(file) > 1000, "Rendered framebuffer PNG written");
            capture = null;
        } catch (Throwable ex) { fail("screenshot " + capture, ex); }
    }
    private static HerobrineScreen panel() {
        if (!(MC.screen instanceof HerobrineScreen screen)) throw new IllegalStateException("Expected real HerobrineScreen, got " + MC.screen);
        return screen;
    }
    private static void select(Ability ability) {
        HerobrineScreen screen = panel();
        for (int i = 0; category(screen) != ability && i < Ability.values().length; i++) {
            screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_ALT);
            screen.tick();
        }
        check(category(screen) == ability, "Keyboard category " + ability);
    }
    private static Ability category(HerobrineScreen screen) { return (Ability) field(screen, "category"); }
    private static AbstractWidget findMessage(String key) {
        return panel().children().stream().filter(item -> item instanceof AbstractWidget).map(item -> (AbstractWidget) item)
                .filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing translated widget " + key));
    }
    private static AbstractWidget findLiteral(String label) {
        return panel().children().stream().filter(item -> item instanceof AbstractWidget).map(item -> (AbstractWidget) item)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static AbstractWidget slider(String key) {
        return panel().children().stream().filter(item -> item instanceof AbstractSliderButton).map(item -> (AbstractWidget) item)
                .filter(widget -> field(widget, "key").equals(key)).findFirst().orElseThrow();
    }
    private static void reveal(AbstractWidget widget) {
        HerobrineScreen screen = panel();
        for (int i = 0; screen.getFocused() != widget && i < screen.children().size() + 2; i++) screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        check(screen.getFocused() == widget && widget.visible, "Widget keyboard reachable");
    }
    private static void click(AbstractWidget widget) { reveal(widget); clickAt(widget, widget.getX() + widget.getWidth() / 2.0); }
    private static void clickAt(AbstractWidget widget, double x) {
        HerobrineScreen screen = panel();
        double y = widget.getY() + widget.getHeight() / 2.0;
        check(screen.mouseClicked(x, y, 0), "Real widget mouse press accepted: " + widget.getMessage().getString());
        screen.mouseReleased(x, y, 0);
    }
    private static void validateLayoutAndNavigation() {
        HerobrineScreen screen = panel();
        int bodyTop = (Integer) field(screen, "bodyTop"), bodyBottom = (Integer) field(screen, "bodyBottom");
        check(bodyBottom > bodyTop, "Usable body viewport");
        for (Ability ability : Ability.values()) {
            select(ability);
            for (var child : screen.children()) if (child instanceof AbstractWidget widget) {
                check(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= screen.width, "Widget within horizontal viewport");
            }
        }
        select(Ability.HEROBRINE);
        for (int i = 0; i < Ability.values().length; i++) screen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
        if (Ability.values().length * 27 > bodyBottom - bodyTop) {
            check((Integer) field(screen, "navScroll") > 0, "Tab reveals scrolled navigation");
        }
        screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0);
        select(Ability.HEROBRINE);
        // A wheel event in the preview or outside the panel must not scroll content.
        int contentScroll = (Integer) field(screen, "contentScroll");
        screen.mouseScrolled(screen.width + 10, bodyTop + 2, -1);
        check(contentScroll == (Integer) field(screen, "contentScroll"), "Wheel hit testing is bounded");
    }
    private static Object field(Object target, String name) {
        try {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }
        catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""; }
    private static void fail(String name, Throwable ex) {
        failed = true;
        results.add("{\"name\":" + quote(name) + ",\"status\":\"failed\",\"detail\":" + quote(ex.toString()) + "}");
        ex.printStackTrace();
        failureCapture = true;
    }
    private static void finish() {
        finished = true;
        try { Files.writeString(output.resolve("client-smoke-results.json"), "{\"realClient\":true,\"failed\":" + failed + ",\"checks\":[\n" + String.join(",\n", results) + "\n]}\n"); }
        catch (Exception ex) { failed = true; ex.printStackTrace(); }
        System.out.println("CLIENT_SMOKE FINISHED failed=" + failed + " checks=" + results.size());
    }
    private record Step(String name, Runnable action, BooleanSupplier done) { }
}
