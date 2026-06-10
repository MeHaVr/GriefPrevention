package com.norvex.crystaleconomy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class CrystalEconomyPlugin extends JavaPlugin
{
    private CrystalDatabase db;
    private CrystalEconomy  economy;

    @Override
    public void onEnable()
    {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        db      = new CrystalDatabase(this);
        economy = new CrystalEconomy(db);

        // Vault Economy registrieren
        getServer().getServicesManager().register(
            Economy.class, economy, this, ServicePriority.Normal);

        // /crystal Befehl registrieren
        CrystalCommand cmd = new CrystalCommand(db, economy);
        getCommand("crystal").setExecutor(cmd);
        getCommand("crystal").setTabCompleter(cmd);

        getLogger().info("CrystalEconomy aktiviert – Crystal-Währung bereit.");
    }

    @Override
    public void onDisable()
    {
        if (db != null) db.close();
        getLogger().info("CrystalEconomy deaktiviert.");
    }
}
