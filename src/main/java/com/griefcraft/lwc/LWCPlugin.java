/*
 * Copyright 2011 Tyler Blair. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package com.griefcraft.lwc;

import com.griefcraft.cache.BlockCache;
import com.griefcraft.listeners.LWC114Listener;
import com.griefcraft.listeners.LWCBlockListener;
import com.griefcraft.listeners.LWCEntityListener;
import com.griefcraft.listeners.LWCPlayerListener;
import com.griefcraft.listeners.LWCServerListener;
import com.griefcraft.scripting.event.LWCCommandEvent;
import com.griefcraft.sql.Database;
import com.griefcraft.util.Metrics;
import com.griefcraft.util.StringUtil;
import com.griefcraft.util.Updater;
import com.griefcraft.util.VersionUtil;
import com.griefcraft.util.locale.LWCResourceBundle;
import com.griefcraft.util.locale.LocaleClassLoader;
import com.griefcraft.util.locale.UTF8Control;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LWCPlugin extends JavaPlugin {

    /**
     * The LWC instance
     */
    private LWC lwc;

    /**
     * The message parser to parse messages with
     */
    private MessageParser messageParser;

    /**
     * LWC updater
     */
    private Updater updater;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        String commandName = command.getName().toLowerCase();
        String argString = StringUtil.join(args, 0);
        boolean isPlayer = (sender instanceof Player); // check if they're a
        // player

        // these can only apply to players, not the console (who has absolute
        // player :P)
        if (isPlayer) {
            // Aliases
            String aliasCommand = null;
            String[] aliasArgs = new String[0];

            if (commandName.equals("cpublic")) {
                aliasCommand = "create";
                aliasArgs = new String[]{"public"};
            } else if (commandName.equals("cpassword")) {
                aliasCommand = "create";
                aliasArgs = ("password " + argString).split(" ");
            } else if (commandName.equals("cprivate") || commandName.equals("lock")) {
                aliasCommand = "create";
                aliasArgs = ("private " + argString).split(" ");
            } else if (commandName.equals("cdonation")) {
                aliasCommand = "create";
                aliasArgs = ("donation " + argString).split(" ");
            } else if (commandName.equals("cmodify")) {
                aliasCommand = "modify";
                aliasArgs = argString.isEmpty() ? new String[0] : argString.split(" ");
            } else if (commandName.equals("cinfo")) {
                aliasCommand = "info";
            } else if (commandName.equals("cunlock")) {
                aliasCommand = "unlock";
                aliasArgs = argString.isEmpty() ? new String[0] : argString.split(" ");
            } else if (commandName.equals("cremove") || commandName.equals("unlock")) {
                aliasCommand = "remove";
                aliasArgs = new String[]{"protection"};
            } else if (commandName.equals("climits")) {
                aliasCommand = "limits";
                aliasArgs = argString.isEmpty() ? new String[0] : argString.split(" ");
            } else if (commandName.equals("cadmin")) {
                aliasCommand = "admin";
                aliasArgs = argString.isEmpty() ? new String[0] : argString.split(" ");
            } else if (commandName.equals("cremoveall")) {
                aliasCommand = "remove";
                aliasArgs = new String[]{"allprotections"};
            }

            // Flag aliases
            if (commandName.equals("credstone")) {
                aliasCommand = "flag";
                aliasArgs = ("redstone " + argString).split(" ");
            } else if (commandName.equals("cmagnet")) {
                aliasCommand = "flag";
                aliasArgs = ("magnet " + argString).split(" ");
            } else if (commandName.equals("cexempt")) {
                aliasCommand = "flag";
                aliasArgs = ("exemption " + argString).split(" ");
            } else if (commandName.equals("cautoclose")) {
                aliasCommand = "flag";
                aliasArgs = ("autoclose " + argString).split(" ");
            } else if (commandName.equals("callowexplosions") || commandName.equals("ctnt")) {
                aliasCommand = "flag";
                aliasArgs = ("allowexplosions " + argString).split(" ");
            } else if (commandName.equals("chopper")) {
                aliasCommand = "flag";
                aliasArgs = ("hopper " + argString).split(" ");
            }

            // Mode aliases
            if (commandName.equals("cdroptransfer")) {
                aliasCommand = "mode";
                aliasArgs = ("droptransfer " + argString).split(" ");
            } else if (commandName.equals("cpersist")) {
                aliasCommand = "mode";
                aliasArgs = ("persist " + argString).split(" ");
            } else if (commandName.equals("cnospam")) {
                aliasCommand = "mode";
                aliasArgs = ("nospam " + argString).split(" ");
            }

            if (aliasCommand != null) {
                lwc.getModuleLoader().dispatchEvent(new LWCCommandEvent(sender, aliasCommand, aliasArgs));
                return true;
            }
        }

        if (args.length == 0) {
            lwc.sendFullHelp(sender);
            return true;
        }

        // Dispatch command to modules
        LWCCommandEvent evt = new LWCCommandEvent(sender, args[0].toLowerCase(),
                args.length > 1 ? StringUtil.join(args, 1).split(" ") : new String[0]);
        lwc.getModuleLoader().dispatchEvent(evt);

        if (evt.isCancelled()) {
            return true;
        }

        if (!isPlayer) {
            lwc.sendLocale(sender, "lwc.commandnotsupported");
            return true;
        }

        // Prevent Bukkit from handling the error which gives a non-descript "/lwc"
        lwc.sendLocale(sender, "lwc.invalidcommand");
        return true;

    }

    @Override
    public void onDisable() {
        LWC.ENABLED = false;

        // Clean up static instances
        if (lwc != null) {
            lwc.destruct();
            BlockCache.destruct();
        }

        // cancel all tasks we created
        getServer().getScheduler().cancelTasks(this);
    }

    @Override
    public void onEnable() {
        lwc = new LWC(this);
        preload();

        // make sure this is a safe version
        Set<String> unsupportedVersions = new HashSet<>(Arrays.asList("1.8", "1.9", "1.10", "1.11", "1.12"));
        Matcher matcher = Pattern.compile("\\d[.]\\d+").matcher(Bukkit.getVersion());
        if (matcher.find() && unsupportedVersions.contains(matcher.group())) {
            this.log("  _       __          __   _____ ");
            this.log(" | |      \\ \\        / /  / ____|");
            this.log(" | |       \\ \\  /\\  / /  | |     ");
            this.log(" | |        \\ \\/  \\/ /   | |     ");
            this.log(" | |____     \\  /\\  /    | |____ ");
            this.log(" |______|     \\/  \\/      \\_____|");
            this.log("");
            this.log("This version of ModernLWC is not compatible with MineCraft " + matcher.group());
            this.log("ModernLWC 2.0.0 and above can only be used on servers running MineCraft 1.13+");
            this.log("Please download an older version of the plugin at " + this.getDescription().getWebsite());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Metrics m = new Metrics(this);

        m.addCustomChart(new Metrics.AdvancedPie("protected_blocks", () -> {
            Map<String, Integer> map = new HashMap<String, Integer>();

            if (lwc.getPhysicalDatabase().getProtectionCount() >= 50000) {
                map.put("Over 50k", 1);
            } else if (lwc.getPhysicalDatabase().getProtectionCount() >= 25000) {
                map.put("Over 25k", 1);
            } else if (lwc.getPhysicalDatabase().getProtectionCount() >= 10000) {
                map.put("Over 10k", 1);
            } else if (lwc.getPhysicalDatabase().getProtectionCount() >= 5000) {
                map.put("Over 5k", 1);
            } else if (lwc.getPhysicalDatabase().getProtectionCount() >= 1000) {
                map.put("Over 1k", 1);
            } else {
                map.put("Under 1k", 1);
            }

            return map;
        }));

        m.addCustomChart(new Metrics.SimplePie("used_language", () -> getCurrentLocale()));

        m.addCustomChart(new Metrics.SimplePie("database_used", () -> {
            String database = lwc.getConfiguration().getString("database.adapter");
            if (database.equalsIgnoreCase("mysql"))
                return "MySQL";

            return "SQLite";
        }));

        LWCInfo.setVersion(getDescription().getVersion());
        LWC.ENABLED = true;

        loadLocales();
        loadDatabase();

        // Load the rest of LWC
        lwc.load();
        registerEvents();
    }

    /**
     * Load the database
     */
    public void loadDatabase() {
        String database = lwc.getConfiguration().getString("database.adapter");

        if (database.equalsIgnoreCase("mysql")) {
            Database.DefaultType = Database.Type.MySQL;
        } else {
            Database.DefaultType = Database.Type.SQLite;
        }
    }

    /**
     * Load LWC localizations
     */
    public void loadLocales() {
        LWCResourceBundle locale;
        String localization = getCurrentLocale();

        File localeDir = new File("plugins/LWC/locale/");
        if (!localeDir.exists()) {
            localeDir.mkdir();
        }

        // located in plugins/LWC/locale/, values in that overrides the ones in
        // the default :-)
        ResourceBundle optionalBundle = null;

        try (JarFile file = new JarFile(getFile())) {
            ResourceBundle defaultBundle;

            // Open the LWC jar file

            // Attempt to load the default locale
            defaultBundle = new PropertyResourceBundle(
                    new InputStreamReader(file.getInputStream(file.getJarEntry("lang/lwc_en.properties")), "UTF-8"));
            locale = new LWCResourceBundle(defaultBundle);

            try {
                optionalBundle = ResourceBundle.getBundle("lwc", new Locale(localization), new LocaleClassLoader(),
                        new UTF8Control());
            } catch (MissingResourceException e) {
            }

            if (optionalBundle != null) {
                locale.addExtensionBundle(optionalBundle);
            }

            // and now check if a bundled locale the same as the server's locale
            // exists
            try {
                optionalBundle = new PropertyResourceBundle(new InputStreamReader(
                        file.getInputStream(file.getJarEntry("lang/lwc_" + localization + ".properties")), "UTF-8"));
            } catch (MissingResourceException e) {
            } catch (NullPointerException e) {
                // file wasn't found :p - that's ok
            }

            // ensure both bundles aren't the same
            if (defaultBundle == optionalBundle) {
                optionalBundle = null;
            }

            if (optionalBundle != null) {
                locale.addExtensionBundle(optionalBundle);
            }
        } catch (MissingResourceException e) {
            log("We are missing the default locale in LWC.jar.. What happened to it? :-(");
            throw e;
        } catch (IOException e) {
            log("Uh-oh: " + e.getMessage());
            return;
        }

        // create the message parser
        messageParser = new SimpleMessageParser(locale);
    }

    /**
     * Load shared libraries and other misc things
     */
    private void preload() {
        updater = new Updater();
        updater.init(); // Check for updates
    }

    /**
     * Log a string to the console
     *
     * @param str
     */
    private void log(String str) {
        getLogger().info(str);
    }

    /**
     * Register all of the events used by LWC
     */
    private void registerEvents() {
        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        pluginManager.registerEvents(new LWCPlayerListener(this), this);
        pluginManager.registerEvents(new LWCEntityListener(this), this);
        pluginManager.registerEvents(new LWCBlockListener(this), this);
        pluginManager.registerEvents(new LWCServerListener(this), this);
        if (VersionUtil.getMinorVersion() > 13) {
            pluginManager.registerEvents(new LWC114Listener(), this);
        }
    }

    /**
     * @return the current locale in use
     */
    public String getCurrentLocale() {
        return lwc.getConfiguration().getString("core.locale", "en");
    }

    /**
     * @return the LWC instance
     */
    public LWC getLWC() {
        return lwc;
    }

    /**
     * Gets the message parser
     *
     * @return
     */
    public MessageParser getMessageParser() {
        return messageParser;
    }

    /**
     * @return the Updater instance
     */
    public Updater getUpdater() {
        return updater;
    }

    @Override
    public File getFile() {
        return super.getFile();
    }
}
