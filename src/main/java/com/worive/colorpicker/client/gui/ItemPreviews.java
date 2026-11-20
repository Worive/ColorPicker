package com.worive.colorpicker.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

final class ItemPreviews {

    private ItemPreviews() {}

    static void draw(Minecraft mc, RenderItem renderer, ItemStack stack, int x, int y, boolean count) {
        if (stack == null) {
            return;
        }
        float brightnessX = OpenGlHelper.lastBrightnessX;
        float brightnessY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            // RenderItem scales block models, so the inventory lighting needs rescaled normals.
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            GL11.glColor4f(1, 1, 1, 1);
            RenderHelper.enableGUIStandardItemLighting();
            FontRenderer font = stack.getItem()
                .getFontRenderer(stack);
            if (font == null) {
                font = mc.fontRenderer;
            }
            renderer.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x, y);
            if (count) {
                renderer.renderItemOverlayIntoGUI(font, mc.getTextureManager(), stack, x, y);
            }
        } finally {
            GL11.glPopAttrib();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        }
    }
}
