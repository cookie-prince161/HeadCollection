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

    // Grau-blaues Fragezeichen-Kopf Icon
    private static final String QUESTION_MARK_TEXTURE = "http://textures.minecraft.net/texture/26e6645a440f73bfb8d274f4a1b0cd0271f52233abd07a477b1e7dd7e84b4bd";

    // Texturen für Mobs ohne nativen Block-Kopf
    private static final Map<EntityType, String> MOB_TEXTURES = new HashMap<>() {{
        put(EntityType.GHAST, "http://textures.minecraft.net/texture/8b6a3b21ac964db0714f970d73760e435f5493da167c1308a32a0d0d82d4");
        put(EntityType.ENDERMITE, "http://textures.minecraft.net/texture/5a133f938fcf52278912d0943265ef0d8be92f7638069a53be86c8f8b80e81c1");
        put(EntityType.SPIDER, "http://textures.minecraft.net/texture/cd541541b7b5087c80918712a4413e1150494cf0722e03d360027f3f18e9d");
        put(EntityType.SHEEP, "http://textures.minecraft.net/texture/f31f9ccc6b3e32f35c36149734570162590ec0e6d60ecf640621379ef510a2f");
        put(EntityType.ENDERMAN, "http://textures.minecraft.net/texture/7aae3104a79815a375e23da1e309dd0e9f1a0e1b6f007f311c10712f5a0459c");
        put(EntityType.BLAZE, "http://textures.minecraft.net/texture/b720ee28f2334863f6aed5ed1ec93e7f91885a535f21e06915809796856417");
        put(EntityType.WARDEN, "http://textures.minecraft.net/texture/918ed9b80ec18d360b9432f913d758f844781423719875e53e414168ee296a");
    }};

    private final List<EntityType> allMobsList = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("headcollection")).setExecutor(this);
        Objects.requireNonNull(getCommand("playerheads")).setExecutor(this);
        Objects.requireNonNull(getCommand("mobheads")).setExecutor(this);

        // Dynamisch ALLE Mobs in Minecraft erfassen
        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type != EntityType.PLAYER) {
                allMobsList.add(type);
            }
        }
        allMobsList.sort(Comparator.comparing(EntityType::name));

        getLogger().info("HeadCollection erfolgreich aktiv! Mobs geladen: " + allMobsList.size());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        Player player = (Player) sender;

        if (label.equalsIgnoreCase("headcollection") || label.equalsIgnoreCase("heads") || label.equalsIgnoreCase("hc")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("testplayer")) {
                String targetName = args[1];
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetName));
                    meta.setDisplayName(ChatColor.YELLOW + "Kopf von " + targetName);
                    head.setItemMeta(meta);
                }
                player.getWorld().dropItemNaturally(player.getLocation(), head);

                playerHeadCollections.putIfAbsent(player.getUniqueId(), new HashSet<>());
                playerHeadCollections.get(player.getUniqueId()).add(targetName);

                player.sendMessage(ChatColor.GREEN + "[Test] Kopf von " + targetName + " gespawnt & freigeschaltet!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                return true;
            }

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

        ItemStack mobIcon = createCustomHead(QUESTION_MARK_TEXTURE, ChatColor.GREEN + "Mob-Köpfe Katalog");
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
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Spieler-Katalog (S. " + (page + 1) + ")");

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

        fillBottomRow(inv, page, allPlayers.size());
        player.openInventory(inv);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer != null) {
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
            return createCustomHead(MOB_TEXTURES.get(type), ChatColor.GOLD + formatMobName(type) + " Kopf");
        }

        // Korrigierte Switch-Anweisung mit den exakten Enum-Konstanten
        Material mat = switch (type) {
            case CREEPER -> Material.CREEPER_HEAD;
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> Material.PLAYER_HEAD;
        };

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + formatMobName(type) + " Kopf");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openMobHeadCatalog(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GREEN + "Mob-Katalog (S. " + (page + 1) + ")");
        Set<String> collectedMobs = mobHeadCollections.getOrDefault(player.getUniqueId(), new HashSet<>());

        int start = page * 45;
        int end = Math.min(start + 45, allMobsList.size());

        for (int i = start; i < end; i++) {
            EntityType type = allMobsList.get(i);
            boolean unlocked = collectedMobs.contains(type.name());

            ItemStack item;
            if (unlocked) {
                item = getMobHeadItem(type);
            } else {
                item = createCustomHead(QUESTION_MARK_TEXTURE, ChatColor.RED + formatMobName(type) + " (Fehlt noch!)");
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setLore(Arrays.asList(ChatColor.DARK_RED + "✘ Noch nicht gefunden", ChatColor.GRAY + "Töte dieses Mob im Survival, um die Chance zu nutzen."));
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(i - start, item);
        }

        fillBottomRow(inv, page, allMobsList.size());
        player.openInventory(inv);
    }

    private void fillBottomRow(Inventory inv, int page, int totalItems) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName(" ");
            glass.setItemMeta(gMeta);
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, glass);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.setDisplayName(ChatColor.YELLOW + "◄ Vorherige Seite");
                prev.setItemMeta(pMeta);
            }
            inv.setItem(45, prev);
        }

        if ((page + 1) * 45 < totalItems) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.setDisplayName(ChatColor.YELLOW + "Nächste Seite ➔");
                next.setItemMeta(nMeta);
            }
            inv.setItem(53, next);
        }
    }

    private String formatMobName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

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
            int slot = event.getSlot();

            if (title.contains("Kopf-Sammlungen")) {
                if (slot == 11) {
                    openPlayerHeadCatalog(player, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                } else if (slot == 15) {
                    openMobHeadCatalog(player, 0);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                }
            } else if (title.contains("Spieler-Katalog")) {
                int currentPage = getCurrentPageFromTitle(title);
                if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    openPlayerHeadCatalog(player, currentPage + 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                } else if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    openPlayerHeadCatalog(player, currentPage - 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                }
            } else if (title.contains("Mob-Katalog")) {
                int currentPage = getCurrentPageFromTitle(title);
                if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    openMobHeadCatalog(player, currentPage + 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                } else if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                    openMobHeadCatalog(player, currentPage - 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                }
            }
        }
    }

    private int getCurrentPageFromTitle(String title) {
        try {
            int start = title.indexOf("S. ") + 3;
            int end = title.indexOf(")");
            return Integer.parseInt(title.substring(start, end)) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
