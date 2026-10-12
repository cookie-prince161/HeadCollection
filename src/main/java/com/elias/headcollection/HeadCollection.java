package com.elias.headcollection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public final class HeadCollection extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Set<String>> playerHeadCollections = new HashMap<>();
    private final Map<UUID, Set<String>> mobHeadCollections = new HashMap<>();

    // Deine Fragezeichen-Textur URL
    private static final String QUESTION_MARK_TEXTURE = "http://textures.minecraft.net/texture/2fcac6928e0c09f22063166cf1ebca8302157688c7b088f7e424e93c9e4e840c";

    // Textur-URLs für Mobs ohne Vanilla-Kopf
    private static final Map<EntityType, String> MOB_TEXTURES = new HashMap<>() {{
        put(EntityType.GHAST, "http://textures.minecraft.net/texture/8b6a3b21ac964db0714f970d73760e435f5493da167c1308a32a0d0d82d4");
        put(EntityType.ENDERMITE, "http://textures.minecraft.net/texture/5a133f938fcf52278912d0943265ef0d8be92f7638069a53be86c8f8b80e81c1");
        put(EntityType.SPIDER, "http://textures.minecraft.net/texture/cd541541b7b5087c80918712a4413e1150494cf0722e03d360027f3f18e9d");
        put(EntityType.SHEEP, "http://textures.minecraft.net/texture/f31f9ccc6b3e32f35c36149734570162590ec0e6d60ecf640621379ef510a2f");
    }};

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("headcollection")).setExecutor(this);
        Objects.requireNonNull(getCommand("playerheads")).setExecutor(this);
        Objects.requireNonNull(getCommand("mobheads")).setExecutor(this);

        getLogger().info("HeadCollection erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HeadCollection deaktiviert.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("headcollection") || label.equalsIgnoreCase("heads") || label.equalsIgnoreCase("hc")) {
            openMainChoiceMenu(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return true;
        } else if (label.equalsIgnoreCase("playerheads")) {
            openPlayerHeadCatalog(player, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            return true;
        } else if (label.equalsIgnoreCase("mobheads")) {
            openMobHeadCatalog(player, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            return true;
        }

        return false;
    }

    private void openMainChoiceMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Kopf-Sammlungen");

        ItemStack playerIcon = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta pMeta = (SkullMeta) playerIcon.getItemMeta();
        if (pMeta != null) {
            pMeta.setOwningPlayer(player);
            pMeta.setDisplayName(ChatColor.GOLD + "Spieler-Trophäen");
            pMeta.setLore(Arrays.asList(ChatColor.GRAY + "Sammle die Köpfe besiegter Spieler!", ChatColor.YELLOW + "Klicken zum Öffnen"));
            playerIcon.setItemMeta(pMeta);
        }

        ItemStack mobIcon = createCustomHead(MOB_TEXTURES.getOrDefault(EntityType.GHAST, QUESTION_MARK_TEXTURE), ChatColor.GREEN + "Mob-Köpfe Katalog");
        ItemMeta mMeta = mobIcon.getItemMeta();
        if (mMeta != null) {
            mMeta.setLore(Arrays.asList(ChatColor.GRAY + "Sammle alle Mob-Köpfe der Welt!", ChatColor.YELLOW + "Klicken zum Öffnen"));
            mobIcon.setItemMeta(mMeta);
        }

        inv.setItem(11, playerIcon);
        inv.setItem(15, mobIcon);
        player.openInventory(inv);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(victim);
                meta.setDisplayName(ChatColor.YELLOW + "Kopf von " + victim.getName());
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Getötet von " + killer.getName()));
                head.setItemMeta(meta);
            }
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);

            UUID killerUUID = killer.getUniqueId();
            playerHeadCollections.putIfAbsent(killerUUID, new HashSet<>());
            Set<String> collected = playerHeadCollections.get(killerUUID);

            if (collected.add(victim.getName())) {
                killer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        Objects.requireNonNull(getConfig().getString("messages.catalog-unlocked")).replace("{name}", victim.getName())));
                killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }

    private void openPlayerHeadCatalog(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Spieler-Katalog (Seite " + (page + 1) + ")");

        List<String> allPlayers = Arrays.stream(Bukkit.getOfflinePlayers())
                .map(OfflinePlayer::getName)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        Set<String> collected = playerHeadCollections.getOrDefault(player.getUniqueId(), new HashSet<>());

        int start = page * 45;
        int end = Math.min(start + 45, allPlayers.size());

        for (int i = start; i < end; i++) {
            String targetName = allPlayers.get(i);
            ItemStack head;

            if (collected.contains(targetName)) {
                head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetName));
                    meta.setDisplayName(ChatColor.GREEN + targetName);
                    meta.setLore(Arrays.asList(ChatColor.DARK_GREEN + "✔ Bereits gesammelt!", ChatColor.GRAY + "Getötet & Trophäe gesichert."));
                    head.setItemMeta(meta);
                }
            } else {
                head = createCustomHead(QUESTION_MARK_TEXTURE, ChatColor.RED + "??? (Fehlt)");
                ItemMeta meta = head.getItemMeta();
                if (meta != null) {
                    meta.setLore(Arrays.asList(ChatColor.DARK_RED + "✘ Noch nicht freigeschaltet", ChatColor.GRAY + "Besiege diesen Spieler, um den Kopf zu erhalten."));
                    head.setItemMeta(meta);
                }
            }
            inv.setItem(i - start, head);
        }

        if (end < allPlayers.size()) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nMeta = nextButton.getItemMeta();
            if (nMeta != null) {
                nMeta.setDisplayName(ChatColor.YELLOW + "Nächste Seite ➔");
                nextButton.setItemMeta(nMeta);
            }
            inv.setItem(53, nextButton);
        }

        player.openInventory(inv);
    }

    private final List<EntityType> trackedMobs = Arrays.asList(
            EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SKELETON,
            EntityType.SPIDER, EntityType.GHAST, EntityType.ENDERMITE, EntityType.SHEEP
    );

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer != null && trackedMobs.contains(entity.getType())) {
            double chance = (killer.getGameMode() == GameMode.CREATIVE)
                    ? 1.0
                    : getConfig().getDouble("drop-chances." + entity.getType().name(), getConfig().getDouble("drop-chances.DEFAULT", 0.05));

            if (Math.random() <= chance) {
                ItemStack mobHead = getMobHeadItem(entity.getType());
                entity.getWorld().dropItemNaturally(entity.getLocation(), mobHead);

                UUID killerUUID = killer.getUniqueId();
                mobHeadCollections.putIfAbsent(killerUUID, new HashSet<>());
                Set<String> collectedMobs = mobHeadCollections.get(killerUUID);

                if (collectedMobs.add(entity.getType().name())) {
                    killer.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            Objects.requireNonNull(getConfig().getString("messages.head-dropped")).replace("{mob}", entity.getType().name().toLowerCase())));
                    killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                }
            }
        }
    }

    private ItemStack getMobHeadItem(EntityType type) {
        if (MOB_TEXTURES.containsKey(type)) {
            return createCustomHead(MOB_TEXTURES.get(type), ChatColor.GOLD + type.name() + " Kopf");
        }

        Material mat = switch (type) {
            case CREEPER -> Material.CREEPER_HEAD;
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            default -> Material.PLAYER_HEAD;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + type.name() + " Kopf");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openMobHeadCatalog(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Mob-Katalog (Seite " + (page + 1) + ")");
        Set<String> collectedMobs = mobHeadCollections.getOrDefault(player.getUniqueId(), new HashSet<>());

        int start = page * 45;
        int end = Math.min(start + 45, trackedMobs.size());

        for (int i = start; i < end; i++) {
            EntityType type = trackedMobs.get(i);
            boolean unlocked = collectedMobs.contains(type.name());

            ItemStack item;
            if (unlocked) {
                item = getMobHeadItem(type);
            } else {
                item = createCustomHead(QUESTION_MARK_TEXTURE, ChatColor.RED + type.name() + " (Fehlt noch!)");
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setLore(Arrays.asList(ChatColor.DARK_RED + "✘ Noch nicht gefunden", ChatColor.GRAY + "Töte dieses Mob im Survival, um die Chance zu nutzen."));
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(i - start, item);
        }

        if (end < trackedMobs.size()) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nMeta = nextButton.getItemMeta();
            if (nMeta != null) {
                nMeta.setDisplayName(ChatColor.YELLOW + "Nächste Seite ➔");
                nextButton.setItemMeta(nMeta);
            }
            inv.setItem(53, nextButton);
        }

        player.openInventory(inv);
    }

    // Modernes & sicheres Erstellen von Custom Heads via Bukkit API
    private ItemStack createCustomHead(String urlString, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (urlString == null || urlString.isEmpty()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();

        try {
            textures.setSkin(new URL(urlString));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        meta.setDisplayName(displayName);
        head.setItemMeta(meta);
        return head;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();

        if (title.contains("Kopf-Sammlungen") || title.contains("Spieler-Katalog") || title.contains("Mob-Katalog")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();

            if (title.contains("Kopf-Sammlungen")) {
                if (event.getSlot() == 11) {
                    openPlayerHeadCatalog(player, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                } else if (event.getSlot() == 15) {
                    openMobHeadCatalog(player, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
            } else if (title.contains("Spieler-Katalog") && event.getSlot() == 53) {
                openPlayerHeadCatalog(player, 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            } else if (title.contains("Mob-Katalog") && event.getSlot() == 53) {
                openMobHeadCatalog(player, 1);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
            }
        }
    }
}
