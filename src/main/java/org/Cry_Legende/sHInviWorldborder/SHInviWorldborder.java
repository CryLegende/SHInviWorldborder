package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SHInviWorldborder extends JavaPlugin implements TabCompleter {

    static final String BYPASS = "shinviworldborder.bypass";
    private static final String RELOAD = "shinviworldborder.admin.reload";
    private static final String INFO = "shinviworldborder.admin.info";
    /** Le serveur ne signale pas les pas de moins de 1/16 de bloc : un joueur à pied peut dépasser le bord de si peu. */
    private static final double WALK_TOLERANCE = 0.1;
    /** Durée, en ticks, pendant laquelle la marge d'un joueur (lue dans ses permissions) est réutilisée. */
    private static final int MARGIN_CACHE_TICKS = 100;

    private record CachedMargin(int tick, double blocks) {
    }

    /** Erreur de lecture d'un fichier de configuration : nom du fichier et cause. */
    private static final class ConfigException extends Exception {
        private final String file;

        ConfigException(String file, String message) {
            super(message);
            this.file = file;
        }

        String getFile() {
            return file;
        }
    }

    /** Réglages, messages et bordures lus ensemble : rien n'est appliqué si l'un des trois fichiers est invalide. */
    private record Loaded(Settings settings, Messages messages, YamlConfiguration borderData) {
    }

    private Settings settings;
    private Messages messages;
    private WorldBorders worlds;
    private IslandBorders islands;
    private BukkitTask particleTask;
    private final Map<UUID, CachedMargin> islandMargins = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration borderData;
        try {
            Loaded loaded = readFiles();
            settings = loaded.settings();
            messages = loaded.messages();
            borderData = loaded.borderData();
        } catch (ConfigException e) {
            getLogger().severe("Fichier " + e.getFile() + " illisible, valeurs du jar utilisées : " + e.getMessage());
            settings = Settings.load(bundled("config.yml"), getLogger());
            messages = new Messages(bundled("messages.yml"));
            borderData = keepBrokenBorderData();
        }
        if (getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
            islands = new IslandBorders();
        } else if (settings.islands().enabled()) {
            getLogger().warning("SuperiorSkyblock2 absent : bordures d'îles désactivées.");
        }
        worlds = new WorldBorders(this, borderData);
        registerWorlds();
        getServer().getPluginManager().registerEvents(new BorderListener(this), this);
        PluginCommand command = getCommand("shinviworldborder");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startParticleTask();
    }

    @Override
    public void onDisable() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        if (worlds != null) {
            worlds.unregisterAll(Bukkit.getWorlds());
        }
    }

    Settings settings() {
        return settings;
    }

    Messages messages() {
        return messages;
    }

    WorldBorders worlds() {
        return worlds;
    }

    /** Bordures d'îles, ou null sans SuperiorSkyblock2. */
    IslandBorders islands() {
        return islands;
    }

    /** Zone où peut se trouver le centre d'un joueur de demi-largeur « halfWidth » dans ce monde, ou null. */
    Area worldBox(World world, double halfWidth) {
        if (!settings.worlds().enabled()) {
            return null;
        }
        Area wall = worlds.area(world);
        return wall == null ? null : wall.expand(settings.worlds().margin() - halfWidth);
    }

    /**
     * Zone où peut se trouver le centre d'un joueur sur l'île de « at ».
     * En vol (ou en élytres) : le bord de l'île plus la marge. À pied : le bord de l'île, le joueur ne peut
     * pas avancer au-dessus du vide sur un bloc posé hors de l'île, que la protection fait aussitôt disparaître.
     * Un joueur qui cesse de voler dans la marge garde la grande zone : il tombe au lieu d'être renvoyé.
     */
    Area islandBox(Player player, Location at, double halfWidth) {
        if (islands == null || !settings.islands().enabled()) {
            return null;
        }
        Area wall = islands.edge(player, at);
        if (wall == null) {
            return null;
        }
        if (!player.isFlying() && !player.isGliding() && wall.contains(at.getX(), at.getZ(), WALK_TOLERANCE)) {
            return wall;
        }
        return wall.expand(islandMargin(player) - halfWidth);
    }

    /** Marge d'île du joueur : la plus large de ses permissions shinviworldborder.margin.<nom>, sinon la marge de base. */
    double islandMargin(Player player) {
        Settings.Border border = settings.islands();
        if (border.margins().isEmpty()) {
            return border.margin();
        }
        int now = Bukkit.getCurrentTick();
        CachedMargin cached = islandMargins.get(player.getUniqueId());
        if (cached != null && now - cached.tick() < MARGIN_CACHE_TICKS) {
            return cached.blocks();
        }
        double blocks = border.margin();
        for (Settings.Margin margin : border.margins()) {
            if (margin.blocks() <= blocks) {
                break;
            }
            if (player.hasPermission(margin.permission())) {
                blocks = margin.blocks();
                break;
            }
        }
        islandMargins.put(player.getUniqueId(), new CachedMargin(now, blocks));
        return blocks;
    }

    boolean ignoresIslandBorder(Player player) {
        return player.hasPermission(BYPASS) || islands.bypass(player);
    }

    void registerWorld(World world) {
        if (settings.worlds().enabled()) {
            worlds.register(world);
        }
    }

    void forget(UUID player) {
        islandMargins.remove(player);
        if (islands != null) {
            islands.forget(player);
        }
    }

    private void registerWorlds() {
        for (World world : Bukkit.getWorlds()) {
            registerWorld(world);
        }
    }

    /** config.yml, messages.yml et borderdata.yml lus strictement (erreur de syntaxe signalée), complétés par le jar. */
    private Loaded readFiles() throws ConfigException {
        YamlConfiguration config = readYaml("config.yml", true);
        YamlConfiguration loadedMessages = readYaml("messages.yml", true);
        YamlConfiguration borderData = readYaml("borderdata.yml", false);
        return new Loaded(Settings.load(config, getLogger()), new Messages(loadedMessages), borderData);
    }

    private YamlConfiguration readYaml(String name, boolean withBundledDefaults) throws ConfigException {
        YamlConfiguration yaml = new YamlConfiguration();
        File file = new File(getDataFolder(), name);
        if (file.exists() || withBundledDefaults) {
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                String reason = e.getMessage() == null ? e.toString() : e.getMessage().lines().findFirst().orElse("");
                throw new ConfigException(name, reason);
            }
        }
        if (withBundledDefaults) {
            yaml.setDefaults(bundled(name));
        }
        return yaml;
    }

    private YamlConfiguration bundled(String name) {
        try (InputStream in = getResource(name)) {
            return in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    /**
     * borderdata.yml illisible au démarrage : une copie est gardée à côté avant que les bordures natives des mondes
     * chargés ne le réécrivent.
     */
    private YamlConfiguration keepBrokenBorderData() {
        File file = new File(getDataFolder(), "borderdata.yml");
        if (file.exists()) {
            try {
                Files.copy(file.toPath(), new File(getDataFolder(), "borderdata.yml.invalid").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().warning("Copie de borderdata.yml impossible : " + e.getMessage());
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void startParticleTask() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        particleTask = Bukkit.getScheduler().runTaskTimer(this, new ParticleTask(this), 20L, settings.intervalTicks());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            messages.send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            messages.send(sender, "admin-usage", "subcommands", String.join("|", allowed));
            return true;
        }
        if (sub.equals("info")) {
            info(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        reload(sender);
        return true;
    }

    private void reload(CommandSender sender) {
        Loaded loaded;
        try {
            loaded = readFiles();
        } catch (ConfigException e) {
            getLogger().severe("Rechargement annulé, " + e.getFile() + " illisible : " + e.getMessage());
            messages.send(sender, "reload-failed", "file", e.getFile(), "error", String.valueOf(e.getMessage()));
            return;
        }
        // Les bordures natives sont remises puis masquées de nouveau : borderdata.yml est relu au passage
        worlds.unregisterAll(Bukkit.getWorlds());
        settings = loaded.settings();
        messages = loaded.messages();
        islandMargins.clear();
        worlds = new WorldBorders(this, loaded.borderData());
        registerWorlds();
        startParticleTask();
        messages.send(sender, "reloaded");
    }

    private void info(CommandSender sender, String worldName) {
        Settings.Border worldSettings = settings.worlds();
        Settings.Border islandSettings = settings.islands();
        List<World> shown = new ArrayList<>();
        if (worldName == null) {
            shown.addAll(Bukkit.getWorlds());
        } else {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                messages.send(sender, "info-unknown-world", "world", worldName);
                return;
            }
            shown.add(world);
        }
        messages.send(sender, "info-header");
        for (World world : shown) {
            WorldBorders.Data data = worlds.data(world);
            if (data == null || data.area() == null) {
                messages.send(sender, "info-world-none", "world", world.getName());
            } else {
                messages.send(sender, "info-world", "world", world.getName(),
                        "x", format(data.centerX()), "z", format(data.centerZ()), "size", format(data.size()));
            }
        }
        List<String> tiers = new ArrayList<>();
        for (Settings.Margin margin : islandSettings.margins()) {
            tiers.add(margin.permission() + " = " + format(margin.blocks()));
        }
        messages.send(sender, "info-settings",
                "worlds", state(worldSettings.enabled()), "islands", state(islandSettings.enabled()),
                "margin", format(islandSettings.margin()), "tiers", tiers.isEmpty() ? "-" : String.join(", ", tiers));
    }

    private String state(boolean enabled) {
        return enabled ? "activées" : "désactivées";
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private List<String> allowed(CommandSender sender) {
        List<String> names = new ArrayList<>();
        if (sender.hasPermission(RELOAD)) {
            names.add("reload");
        }
        if (sender.hasPermission(INFO)) {
            names.add("info");
        }
        return names;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return allowed(sender).stream().filter(name -> name.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission(INFO)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getWorlds().stream().map(World::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
