package com.worive;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Mod(
    modid = "colorpicker",
    name = "ColorPicker",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public final class ColorPickerMod {

    static final Logger LOG = LogManager.getLogger("ColorPicker");

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide()
            .isClient()) {
            new Client().register();
        }
    }

    @SideOnly(Side.CLIENT)
    public static final class Client {

        private final Minecraft minecraft = Minecraft.getMinecraft();
        // Keep the original key ID so Minecraft can load existing bindings from options.txt.
        private final KeyBinding open = new KeyBinding(
            "key.colourpicker.open",
            Keyboard.KEY_G,
            "key.categories.colorpicker");
        private final BlockColours colours = new BlockColours();
        private final PickerScreen.State state = new PickerScreen.State(configFile(minecraft.mcDataDir));
        private final Gradient gradient = new Gradient();

        private static File configFile(File gameDirectory) {
            File config = new File(gameDirectory, "config/colorpicker.cfg");
            File legacyConfig = new File(gameDirectory, "config/colourpicker.cfg");
            return !config.exists() && legacyConfig.isFile() ? legacyConfig : config;
        }

        void register() {
            if (Loader.isModLoaded("NotEnoughItems")) {
                NeiHoverHandler.register();
            }
            ClientRegistry.registerKeyBinding(open);
            FMLCommonHandler.instance()
                .bus()
                .register(this);
            ((IReloadableResourceManager) minecraft.getResourceManager()).registerReloadListener(colours);
        }

        @SubscribeEvent
        public void keyPressed(InputEvent.KeyInputEvent event) {
            if (open.isPressed() && minecraft.currentScreen == null && minecraft.thePlayer != null) {
                show(null, minecraft.objectMouseOver, false);
            }
        }

        private void show(GuiScreen parent, MovingObjectPosition target, boolean showGradient) {
            int key = open.getKeyCode();
            if (showGradient) {
                minecraft.displayGuiScreen(
                    new GradientScreen(parent, colours, state, gradient, key, other -> show(parent, target, other)));
            } else {
                minecraft.displayGuiScreen(
                    new PickerScreen(parent, colours, state, target, key, other -> show(parent, target, other)));
            }
        }
    }
}
