package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SHInviWorldborder extends JavaPlugin implements TabCompleter {

    static final String BYPASS = "shinviworldborder.bypass";
    private static final String ADMIN = "shinviworldborder.admin";
    /** Le serveur ne signale pas les pas de moins de 1/16 de bloc : un joueur à pied peut dépasser le bord de si peu. */
    private static final double WALK_TOLERANCE = 0.1;

    private Settings settings;
    private Messages messages;
    private WorldBorders worlds;
    private IslandBorders islands;
    private BukkitTask particleTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        loadFiles();
        if (getServer().getPluginManager().isPluginEnabled("SuperiorSkyblock2")) {
            islands = new IslandBorders();
        } else if (settings.islands().enabled()) {
            getLogger().warning("SuperiorSkyblock2 absent : bordures d'îles désactivées.");
        }
        worlds = new WorldBorders(this);
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
        Area flight = wall.expand(settings.islands().margin() - halfWidth);
        if (player.isFlying() || player.isGliding()) {
            return flight;
        }
        return wall.contains(at.getX(), at.getZ(), WALK_TOLERANCE) ? wall : flight;
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
        if (islands != null) {
            islands.forget(player);
        }
    }

    private void registerWorlds() {
        for (World world : Bukkit.getWorlds()) {
            registerWorld(world);
        }
    }

    private void loadFiles() {
        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        messages = new Messages(new File(getDataFolder(), "messages.yml"));
    }

    private void startParticleTask() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        particleTask = Bukkit.getScheduler().runTaskTimer(this, new ParticleTask(this), 20L, settings.intervalTicks());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            messages.send(sender, "usage");
            return true;
        }
        // Les bordures natives sont remises puis masquées de nouveau : borderdata.yml est relu au passage
        worlds.unregisterAll(Bukkit.getWorlds());
        loadFiles();
        worlds = new WorldBorders(this);
        registerWorlds();
        startParticleTask();
        messages.send(sender, "reloaded");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }
}
