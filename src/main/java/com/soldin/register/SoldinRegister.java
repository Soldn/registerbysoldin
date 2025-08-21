package com.soldin.register;

import org.bukkit.plugin.java.JavaPlugin;

public class SoldinRegister extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("SoldinRegister enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SoldinRegister disabled!");
    }
}
