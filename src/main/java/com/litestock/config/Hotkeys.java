package com.litestock.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import com.litestock.LiteStock;

import java.util.List;

public class Hotkeys {
    private static final String HOTKEYS_KEY = LiteStock.MOD_ID + ".config.hotkeys";

    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey(
            "openConfigGui", "L,O",
            HOTKEYS_KEY + ".comment.openConfigGui",
            "打开配置界面",
            HOTKEYS_KEY + ".name.openConfigGui"
    );

    public static final ConfigHotkey TOGGLE_SCAN = new ConfigHotkey(
            "toggleScan", "H",
            HOTKEYS_KEY + ".comment.toggleScan",
            "切换扫描",
            HOTKEYS_KEY + ".name.toggleScan"
    );

    public static final ConfigHotkey ADD_CONTAINER = new ConfigHotkey(
            "addContainer", "K",
            HOTKEYS_KEY + ".comment.addContainer",
            "框选添加容器",
            HOTKEYS_KEY + ".name.addContainer"
    );

    public static final ConfigHotkey CLEAR_SELECTION = new ConfigHotkey(
            "clearSelection", "L",
            HOTKEYS_KEY + ".comment.clearSelection",
            "清除当前选区",
            HOTKEYS_KEY + ".name.clearSelection"
    );

    public static final ConfigHotkey SEARCH_ITEM = new ConfigHotkey(
            "searchItem", "J",
            HOTKEYS_KEY + ".comment.searchItem",
            "按物品名搜索箱子",
            HOTKEYS_KEY + ".name.searchItem"
    );

    public static final ConfigHotkey ADD_SEARCH_BLOCK = new ConfigHotkey(
            "addSearchBlock", "",
            "在选区内添加搜索方块，无法搜到的方块存储在本地，方便下次搜索",
            "添加方块",
            HOTKEYS_KEY + ".name.addSearchBlock"
    );

    public static final List<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
            OPEN_CONFIG_GUI,
            TOGGLE_SCAN,
            ADD_CONTAINER,
            CLEAR_SELECTION,
            SEARCH_ITEM,
            ADD_SEARCH_BLOCK
    );
}
