package com.worive.colorpicker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

import com.worive.colorpicker.client.color.BlockColors;
import com.worive.colorpicker.client.color.Gradient;
import com.worive.colorpicker.client.gui.GradientScreen;
import com.worive.colorpicker.client.gui.NeiHoverHandler;
import com.worive.colorpicker.client.gui.PickerColor;
import com.worive.colorpicker.client.gui.PickerScreen;
import com.worive.colorpicker.client.gui.PickerSettings;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mod(
    modid = "colorpicker",
    name = "ColorPicker",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public final class ColorPickerMod {

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide()
            .isClient()) {
            new Client().register();
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class Client {

        private static final long WORLD_INDEX_BUDGET_NANOS = 1_000_000;

        private final Minecraft minecraft = Minecraft.getMinecraft();
        private final KeyBinding open = new KeyBinding(
            "key.colorpicker.open",
            Keyboard.KEY_G,
            "key.categories.colorpicker");
        private final BlockColors colors = new BlockColors();
        private final PickerSettings settings = new PickerSettings(minecraft.mcDataDir);
        private final PickerColor pickerColor = new PickerColor();
        private final Gradient gradient = new Gradient();
        private WorldClient indexWorld;

        void register() {
            if (Loader.isModLoaded("NotEnoughItems")) {
                NeiHoverHandler.register();
            }
            loadLegacyKeyBinding();
            ClientRegistry.registerKeyBinding(open);
            FMLCommonHandler.instance()
                .bus()
                .register(this);
            ((IReloadableResourceManager) minecraft.getResourceManager()).registerReloadListener(colors);
        }

        private void loadLegacyKeyBinding() {
            File options = new File(minecraft.mcDataDir, "options.txt");
            if (!options.isFile()) {
                return;
            }
            // Forge reloads options after registration, so a saved value for the current key ID takes precedence.
            String legacyKey = "key_key.colourpicker.open:";
            try (BufferedReader reader = new BufferedReader(new FileReader(options))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(legacyKey)) {
                        try {
                            open.setKeyCode(Integer.parseInt(line.substring(legacyKey.length())));
                        } catch (NumberFormatException invalid) {
                            LogManager.getLogger("ColorPicker")
                                .debug("Could not parse the previous key binding", invalid);
                        }
                    }
                }
            } catch (IOException failure) {
                LogManager.getLogger("ColorPicker")
                    .debug("Could not read the previous key binding", failure);
            }
        }

        @SubscribeEvent
        public void keyPressed(InputEvent.KeyInputEvent event) {
            if (open.isPressed() && minecraft.currentScreen == null && minecraft.thePlayer != null) {
                show(null, minecraft.objectMouseOver, false);
            }
        }

        @SubscribeEvent
        public void indexBlocks(TickEvent.RenderTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            WorldClient world = minecraft.theWorld;
            if (world != indexWorld) {
                if (indexWorld != null) {
                    colors.resetWorldIndex();
                }
                indexWorld = world;
            }
            GuiScreen screen = minecraft.currentScreen;
            if (world != null && minecraft.thePlayer != null
                && !(screen instanceof GuiDownloadTerrain)
                && (settings.indexOnWorldJoin() || screen instanceof PickerScreen
                    || screen instanceof GradientScreen)) {
                colors.advanceIndex(WORLD_INDEX_BUDGET_NANOS);
            }
        }

        private void show(GuiScreen parent, MovingObjectPosition target, boolean showGradient) {
            int key = open.getKeyCode();
            if (showGradient) {
                minecraft.displayGuiScreen(
                    new GradientScreen(parent, colors, settings, gradient, key, other -> show(parent, target, other)));
            } else {
                minecraft.displayGuiScreen(
                    new PickerScreen(
                        parent,
                        colors,
                        settings,
                        pickerColor,
                        target,
                        key,
                        other -> show(parent, target, other)));
            }
        }
    }
}
