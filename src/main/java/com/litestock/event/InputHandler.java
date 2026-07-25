package com.litestock.event;

import com.litestock.LiteStock;
import com.litestock.config.Hotkeys;
import com.litestock.gui.GuiConfigs;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.Minecraft;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {
    public InputHandler() {
        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new KeyCallbackOpenConfigGui());
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(LiteStock.MOD_ID, "litestock.hotkeys.category.hotkeys", Hotkeys.HOTKEY_LIST);
    }

    private static class KeyCallbackOpenConfigGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && action == KeyAction.PRESS) {
                mc.setScreen(new GuiConfigs());
                return true;
            }
            return false;
        }
    }
}
