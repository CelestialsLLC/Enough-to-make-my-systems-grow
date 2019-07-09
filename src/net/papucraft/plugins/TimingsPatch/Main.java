package net.papucraft.plugins.TimingsPatch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.TimingsCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

public class Main extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getConsoleSender().sendMessage(ChatColor.GRAY + "§m----------§7 [ §bTimingsPatch 1.1 §7] §m----------");
        getServer().getConsoleSender().sendMessage(ChatColor.GRAY + "This server is using TimingsPatch by PapuCraft Network");
        getServer().getConsoleSender().sendMessage(ChatColor.DARK_GRAY + "Join to the server with mc.papucraft.net");
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "Thanks for using TimingsPatch");
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if ((event.getMessage().startsWith("/timings paste")) && (event.getPlayer().hasPermission("timings.patch"))) {
            handle(event.getPlayer());
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (event.getCommand().startsWith("timings paste")) {
            handle(event.getSender());
            event.setCommand("timingspatcher");
        }
    }
    
    private void handle(CommandSender sender) {
        if (!getServer().getPluginManager().useTimings()) {
            sender.sendMessage("Включи тики в конфиге: \"settings.plugin-profiling\" в bukkit.yml");
            return;
        }
        
        if (!getServer().getPluginManager().useTimings()) {
            sender.sendMessage("Включи тики командой /timings on");
            return;
        }
        
        long sampleTime = System.nanoTime() - TimingsCommand.timingStart;
        int index = 0;
        File timingFolder = new File("timings");
        timingFolder.mkdirs();
        File timings = new File(timingFolder, "timings.txt");
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        
        while (timings.exists()) {
            timings = new File(timingFolder, "timings" + ++index + ".txt");
        }
        
        PrintStream fileTimings = new PrintStream(bout);
        
        org.spigotmc.CustomTimingsHandler.printTimings(fileTimings);
        fileTimings.println("Sample time " + sampleTime + " (" + sampleTime / 1.0E9D + "s)");
        
        fileTimings.println("<spigotConfig>");
        fileTimings.println(Bukkit.spigot().getConfig().saveToString());
        fileTimings.println("</spigotConfig>");
        
        new PasteThread(sender, bout).start();
    }
    
    
    private static class PasteThread extends Thread {
        
        private final CommandSender sender;
        private final ByteArrayOutputStream bout;
        
        PasteThread(CommandSender sender, ByteArrayOutputStream bout) {
            this.sender = sender;
            this.bout = bout;
        }

        @Override
        public synchronized void start() {
            if ((sender instanceof org.bukkit.command.RemoteConsoleCommandSender)) {
                run();
            } else {
                super.start();
            }
        }

        @Override
        public void run() {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL("https://timings.spigotmc.org/paste").openConnection();
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setInstanceFollowRedirects(false);
                
                OutputStream out = con.getOutputStream();
                Throwable localThrowable3 = null;
                
                try {
                    out.write(bout.toByteArray());
                } catch (Throwable localThrowable1) {
                    localThrowable3 = localThrowable1;
                    throw localThrowable1;
                } finally {
                    if (out != null)
                        if (localThrowable3 != null)
                            try { out.close(); } catch (Throwable localThrowable2) {
                                localThrowable3.addSuppressed(localThrowable2);
                            }
                        else
                            out.close();
                }
                
                JsonObject location = new Gson().fromJson(new java.io.InputStreamReader(con.getInputStream()), JsonObject.class);
                con.getInputStream().close();
                
                String pasteID = location.get("key").getAsString();
                sender.sendMessage(ChatColor.GREEN + "Тики сервера можешь посмотреть здесь https://www.spigotmc.org/go/timings?url=" + pasteID);
            } catch (IOException ex) {
                sender.sendMessage(ChatColor.RED + "Ошибка. Проверь консоль");
                Bukkit.getServer().getLogger().log(Level.WARNING, "Невозможно запустить парсерс тиков: ", ex);
            }
        }
    }
}
