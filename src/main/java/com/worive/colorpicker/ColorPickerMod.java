package com.worive.colorpicker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

import org.lwjgl.input.Keyboard;

import com.worive.colorpicker.client.colour.BlockColours;
import com.worive.colorpicker.client.colour.Gradient;
import com.worive.colorpicker.client.gui.GradientScreen;
import com.worive.colorpicker.client.gui.NeiHoverHandler;
import com.worive.colorpicker.client.gui.PickerColour;
import com.worive.colorpicker.client.gui.PickerScreen;
import com.worive.colorpicker.client.gui.PickerSettings;

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
        private final PickerSettings settings = new PickerSettings(minecraft.mcDataDir);
        private final PickerColour pickerColour = new PickerColour();
        private final Gradient gradient = new Gradient();

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
                    new GradientScreen(parent, colours, settings, gradient, key, other -> show(parent, target, other)));
            } else {
                minecraft.displayGuiScreen(
                    new PickerScreen(
                        parent,
                        colours,
                        settings,
                        pickerColour,
                        target,
                        key,
                        other -> show(parent, target, other)));
            }
        }
    }
}
