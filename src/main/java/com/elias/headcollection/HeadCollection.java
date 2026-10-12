import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public final class HeadCollection extends JavaPlugin implements Listener, CommandExecutor {

    // Datenspeicher (In-Memory / Vereinfacht für Prototyp, wird über YAML/Daten erweitert)
    // Map<UUID, Set<String>> -> Spieler-UUID -> Liste gesammelter Spieler-Namen / Mob-Namen
    private final Map<UUID, Set<String>> playerHeadCollections = new HashMap<>();
    private final Map<UUID, Set<String>> mobHeadCollections = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        
        // Commands registrieren
        Objects.requireNonNull(getCommand("headcollection")).setExecutor(this);
        Objects.requireNonNull(getCommand("playerheads")).setExecutor(this);
        Objects.requireNonNull(getCommand("mobheads")).setExecutor(this);

        getLogger().info("HeadCollection erfolgreich aktiviert! Test-Dropchancen stehen auf 100%.");
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

    // --- GUI: Hauptauswahl zwischen Spieler- und Mob-Katalog ---
    private void openMainChoiceMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Kopf-Sammlungen");

        // Spieler-Köpfe Icon
        ItemStack playerIcon = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta pMeta = (SkullMeta) playerIcon.getItemMeta();
        if (pMeta != null) {
            pMeta.setOwningPlayer(player);
            pMeta.setDisplayName(ChatColor.GOLD + "Spieler-Trophäen");
            pMeta.setLore(Arrays.asList(ChatColor.GRAY + "Sammle die Köpfe besiegter Spieler!", ChatColor.YELLOW + "Klicken zum Öffnen"));
            playerIcon.setItemMeta(pMeta);
        }

        // Mob-Köpfe Icon
        ItemStack mobIcon = new ItemStack(Material.CREEPER_HEAD);
        ItemMeta mMeta = mobIcon.getItemMeta();
        if (mMeta != null) {
            mMeta.setDisplayName(ChatColor.GREEN + "Mob-Köpfe Katalog");
            mMeta.setLore(Arrays.asList(ChatColor.GRAY + "Sammle alle Mob-Köpfe der Welt!", ChatColor.YELLOW + "Klicken zum Öffnen"));
            mobIcon.setItemMeta(mMeta);
        }

        inv.setItem(11, playerIcon);
        inv.setItem(15, mobIcon);
        player.openInventory(inv);
    }

    // --- SPIELER-KÖPFE LOGIK & GUI ---
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            // Kopf des Opfers droppen
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(victim);
                meta.setDisplayName(ChatColor.YELLOW + "Kopf von " + victim.getName());
                meta.setLore(Collections.singletonList(ChatColor.GRAY + "Getötet von " + killer.getName()));
                head.setItemMeta(meta);
            }
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);

            // Zum Katalog des Killers hinzufügen (falls erster Kill)
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
        // Double-Chest mit 54 Slots (Zeile 6 reserviert für Navigation)
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Spieler-Katalog (Seite " + (page + 1) + ")");
        
        // Alle bekannten Spieler auf dem Server holen (oder Beispiel-Liste)
        List<String> allPlayers = Bukkit.getOfflinePlayers().stream()
                .map(OfflinePlayer::getName)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        Set<String> collected = playerHeadCollections.getOrDefault(player.getUniqueId(), new HashSet<>());

        int start = page * 45;
        int end = Math.min(start + 45, allPlayers.size());

        for (int i = start; i < end; i++) {
            String targetName = allPlayers.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(targetName);
                meta.setOwningPlayer(offPlayer);
                if (collected.contains(targetName)) {
                    meta.setDisplayName(ChatColor.GREEN + targetName);
                    meta.setLore(Arrays.asList(ChatColor.DARK_GREEN + "✔ Bereits gesammelt!", ChatColor.GRAY + "Getötet & Trophäe gesichert."));
                } else {
                    meta.setDisplayName(ChatColor.RED + "??? (Fehlt)");
                    meta.setLore(Arrays.asList(ChatColor.DARK_RED + "✘ Noch nicht freigeschaltet", ChatColor.GRAY + "Besiege diesen Spieler, um den Kopf zu erhalten."));
                }
                head.setItemMeta(meta);
            }
            inv.setItem(i - start, head);
        }

        // Navigation in der untersten Zeile (Slot 53 = Nächste Seite)
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

    // --- MOB-KÖPFE LOGIK & GUI ---
    private final List<EntityType> trackedMobs = Arrays.asList(
            EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SKELETON, 
            EntityType.SPIDER, EntityType.GHAST, EntityType.ENDERMITE, EntityType.SHEEP
    );

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer != null && trackedMobs.contains(entity.getType())) {
            // Mit 100% Dropchance für Tests
            double chance = getConfig().getDouble("drop-chances." + entity.getType().name(), 1.0);
            if (Math.random() <= chance) {
                ItemStack mobHead = getMobHeadItem(entity.getType());
                entity.getWorld().dropItemNaturally(entity.getLocation(), mobHead);

                // Katalog-Eintrag aktualisieren
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
        Material mat = switch (type) {
            case CREEPER -> Material.CREEPER_HEAD;
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            default -> Material.PLAYER_HEAD; // Fallback
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
                // Platzhalter / Fragezeichen-Kopf für fehlende Mobs
                item = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.RED + type.name() + " (Fehlt noch!)");
                    meta.setLore(Arrays.asList(ChatColor.DARK_RED + "✘ Noch nicht gefunden", ChatColor.GRAY + "Töte dieses Mob, um die Chance zu nutzen."));
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

    // --- GUI-KLICK INTERAKTIONEN ---
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
