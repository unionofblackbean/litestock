package com.litestock.litematica;

import com.litestock.LiteStock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品ID别名映射系统。
 *
 * <p>解决的问题：不同版本的 Minecraft 中物品ID可能发生变化（如 redstone_dust → redstone），
 * 或外部 mod（如 fangkuai-material）使用的物品ID与实际注册名不一致。
 *
 * <p>当从 HUD 字符串解析 Item 时，如果主ID找不到，会自动尝试所有别名ID。
 */
public class ItemAlias {
    private static final Map<String, List<String>> aliasGroups = new HashMap<>();

    static {
        // 红石粉：1.21.4+ 从 redstone_dust 改名为 redstone
        aliasGroups.put("minecraft:redstone", List.of("minecraft:redstone_dust", "minecraft:redstone"));
        aliasGroups.put("minecraft:redstone_dust", List.of("minecraft:redstone_dust", "minecraft:redstone"));

        // 可以在这里添加更多的物品ID映射
        // aliasGroups.put("old_id", List.of("new_id", "old_id"));
    }

    /**
     * 尝试通过物品ID字符串查找 Item 对象。
     * 如果主ID找不到，会自动尝试别名ID。
     */
    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;

        // 先直接尝试
        Item item = tryParse(itemId);
        if (item != null) return item;

        // 尝试别名
        List<String> aliases = aliasGroups.get(itemId);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias.equals(itemId)) continue;
                item = tryParse(alias);
                if (item != null) {
                    LiteStock.LOGGER.debug("ItemAlias: '{}' resolved to '{}' via alias", itemId, alias);
                    return item;
                }
            }
        }

        return null;
    }

    /**
     * 给定一个 Item，返回所有匹配的 Item 集合（包括别名）。
     * 用于容器匹配时的双向查找。
     */
    public static java.util.Set<Item> getMatchingItems(Item item) {
        java.util.Set<Item> result = new java.util.HashSet<>();
        if (item == null) return result;
        result.add(item);

        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        List<String> aliases = aliasGroups.get(id);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias.equals(id)) continue;
                Item aliasItem = tryParse(alias);
                if (aliasItem != null) {
                    result.add(aliasItem);
                }
            }
        }

        // 反向查找：如果某个alias组包含此item的ID，添加组内所有item
        for (Map.Entry<String, List<String>> entry : aliasGroups.entrySet()) {
            if (entry.getValue().contains(id)) {
                for (String alias : entry.getValue()) {
                    if (alias.equals(id)) continue;
                    Item aliasItem = tryParse(alias);
                    if (aliasItem != null) {
                        result.add(aliasItem);
                    }
                }
            }
        }

        return result;
    }

    private static Item tryParse(String itemId) {
        try {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) return null;
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
