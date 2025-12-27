package ru.gdev.seecustomcrafts;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SeeCustomCrafts extends JavaPlugin implements Listener {

    private final Map<String, ItemStack[]> recipeCache = new HashMap<>();

    private final int[] inputSlots = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };
    private final int resultSlot = 24;
    private final int saveSlot = 38;
    private final int cancelSlot = 42;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadRecipes();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void loadRecipes() {
        recipeCache.clear();
        File dir = new File(getDataFolder(), "recipes");
        if (!dir.exists()) dir.mkdirs();
        for (File file : Objects.requireNonNull(dir.listFiles((d, name) -> name.endsWith(".yml")))) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            List<?> list = yml.getList("items");
            if (list != null && list.size() == 10) {
                ItemStack[] items = list.toArray(new ItemStack[0]);
                String id = file.getName().replace(".yml", "");
                recipeCache.put(id, items);
                registerRecipe(id, items);
            }
        }
    }

    private void registerRecipe(String id, ItemStack[] items) {
        ItemStack result = items[9];
        if (result == null || result.getType() == Material.AIR) return;

        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), result.clone());
        recipe.shape("ABC", "DEF", "GHI");
        char[] keys = "ABCDEFGHI".toCharArray();
        Map<Character, ItemStack> map = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            ItemStack item = items[i];
            if (item != null && item.getType() != Material.AIR) {
                map.put(keys[i], item.clone());
            } else {
                map.put(keys[i], null);
            }
        }

        for (char c : keys) {
            ItemStack i = map.get(c);
            if (i != null && i.getType() != Material.AIR) {
                recipe.setIngredient(c, new RecipeChoice.ExactChoice(i));
            } else {
                recipe.setIngredient(c, Material.AIR);
            }
        }

        Bukkit.addRecipe(recipe);
    }

    private void openCreateGUI(Player player, String id, boolean edit) {
        if (!edit && recipeCache.containsKey(id)) {
            player.sendMessage("§cРецепт с ID '" + id + "' уже существует.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, (edit ? "§bРедактирование: " : "§aСоздание: ") + id);
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            background.setItemMeta(meta);
        }

        for (int i = 0; i < 54; i++) gui.setItem(i, background);

        for (int slot : inputSlots) gui.setItem(slot, null);
        gui.setItem(resultSlot, null);

        ItemStack save = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName("§aСохранить");
            save.setItemMeta(saveMeta);
        }

        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§cОтмена");
            cancel.setItemMeta(cancelMeta);
        }

        gui.setItem(saveSlot, save);
        gui.setItem(cancelSlot, cancel);

        if (edit && recipeCache.containsKey(id)) {
            ItemStack[] items = recipeCache.get(id);
            for (int i = 0; i < Math.min(items.length, inputSlots.length); i++) {
                gui.setItem(inputSlots[i], items[i]);
            }
            if (items.length > 9) gui.setItem(resultSlot, items[9]);
        }

        player.openInventory(gui);
        player.setMetadata("craft-id", new FixedMetadataValue(this, id));
    }

    private void saveRecipe(Player player, Inventory inv) {
        if (!player.hasMetadata("craft-id")) return;
        String id = player.getMetadata("craft-id").get(0).asString();
        File file = new File(getDataFolder(), "recipes/" + id + ".yml");

        List<ItemStack> items = new ArrayList<>();
        for (int slot : inputSlots) items.add(inv.getItem(slot));
        items.add(inv.getItem(resultSlot));

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("items", items);
        try {
            yml.save(file);
            recipeCache.put(id, items.toArray(new ItemStack[0]));
            registerRecipe(id, items.toArray(new ItemStack[0]));
            player.sendMessage("§aРецепт '" + id + "' успешно сохранён и зарегистрирован.");
        } catch (IOException e) {
            player.sendMessage("§cОшибка при сохранении рецепта.");
        }
    }

    private void openListGUI(Player player, String filter, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, "§eСписок рецептов — стр. " + page);
        ItemStack background = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            background.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) inv.setItem(i, background);

        List<String> keys = new ArrayList<>();
        for (String key : recipeCache.keySet()) {
            if (filter == null || key.toLowerCase().contains(filter.toLowerCase())) {
                keys.add(key);
            }
        }

        int start = (page - 1) * 28;
        int index = 0;
        for (int i = start; i < keys.size() && index < 28; i++) {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta m = item.getItemMeta();
            if (m != null) {
                m.setDisplayName("§a" + keys.get(i));
                item.setItemMeta(m);
            }
            inv.setItem(index++, item);
        }

        player.openInventory(inv);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (args.length == 0) return false;

        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) return false;
                openCreateGUI(player, args[1], false);
                break;
            case "edit":
                if (args.length < 2) return false;
                openCreateGUI(player, args[1], true);
                break;
            case "list":
                String filter = args.length >= 2 ? args[1] : null;
                int page = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
                openListGUI(player, filter, page);
                break;
            case "remove":
                if (args.length < 2) return false;
                File file = new File(getDataFolder(), "recipes/" + args[1] + ".yml");
                if (file.exists()) {
                    file.delete();
                    recipeCache.remove(args[1]);
                    player.sendMessage("§cРецепт '" + args[1] + "' удалён.");
                } else {
                    player.sendMessage("§cРецепт не найден.");
                }
                break;
            case "reload":
                reloadConfig();
                loadRecipes();
                player.sendMessage("§aКонфигурация и рецепты перезагружены.");
                break;
        }
        return true;
    }

    @org.bukkit.event.EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity entity = e.getWhoClicked();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        String title = e.getView().getTitle();
        if (title.startsWith("§aСоздание:") || title.startsWith("§bРедактирование:")) {
            int slot = e.getRawSlot();
            if (slot >= 0 && slot < 54 && !isEditableSlot(slot)) e.setCancelled(true);
            if (slot == cancelSlot) {
                e.setCancelled(true);
                player.closeInventory();
                player.sendMessage("§cСоздание рецепта отменено.");
            } else if (slot == saveSlot) {
                e.setCancelled(true);
                saveRecipe(player, e.getInventory());
                player.closeInventory();
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer().hasMetadata("craft-id")) {
            e.getPlayer().removeMetadata("craft-id", this);
        }
    }

    private boolean isEditableSlot(int slot) {
        for (int i : inputSlots) if (slot == i) return true;
        return slot == resultSlot;
    }
}
