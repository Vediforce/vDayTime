package org.vediforce.vDayTime

import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.WeakHashMap

class VDayTime : JavaPlugin(), CommandExecutor {

    private var dayDurationMinutes: Long = 10
    private var nightDurationMinutes: Long = 10
    private var timeTask: BukkitTask? = null
    private val timeAccumulators = WeakHashMap<World, Double>()

    override fun onEnable() {
        // Load configuration and start the time management task
        reloadPlugin()
        // Register the command executor
        getCommand("vdaytimereload")?.setExecutor(this)
        logger.info("vDayTime has been enabled successfully!")
    }

    override fun onDisable() {
        // Stop the task when the plugin is disabled
        timeTask?.cancel()
        timeAccumulators.clear()
        logger.info("vDayTime has been disabled.")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("vdaytimereload", ignoreCase = true)) {
            // Permission is already checked in plugin.yml, but a manual check is good practice
            if (!sender.hasPermission("vdaytime.reload")) {
                sender.sendMessage("${ChatColor.RED}You do not have permission to use this command.")
                return true
            }
            reloadPlugin()
            sender.sendMessage("${ChatColor.GREEN}vDayTime configuration reloaded successfully!")
            return true
        }
        return false
    }

    private fun reloadPlugin() {
        // Cancel any existing task
        timeTask?.cancel()
        // Clear accumulators before restarting
        timeAccumulators.clear()
        // Load config values
        setupConfig()
        // Start the scheduler with new values
        startScheduler()
    }

    private fun setupConfig() {
        saveDefaultConfig()
        reloadConfig()

        dayDurationMinutes = config.getLong("day-duration-minutes", 10)
        nightDurationMinutes = config.getLong("night-duration-minutes", 10)

        // Ensure durations are positive to prevent division by zero or other issues
        if (dayDurationMinutes <= 0) {
            logger.warning("'day-duration-minutes' is invalid (${dayDurationMinutes}). Using default value of 10.")
            dayDurationMinutes = 10
        }
        if (nightDurationMinutes <= 0) {
            logger.warning("'night-duration-minutes' is invalid (${nightDurationMinutes}). Using default value of 10.")
            nightDurationMinutes = 10
        }
    }

    private fun startScheduler() {
        // Calculate how many game ticks to add each server tick for day and night
        val dayTickIncrement = 12000.0 / (dayDurationMinutes * 60 * 20)
        val nightTickIncrement = 12000.0 / (nightDurationMinutes * 60 * 20)

        timeTask = object : BukkitRunnable() {
            override fun run() {
                server.worlds.forEach { world ->
                    // This plugin only modifies worlds where the vanilla daylight cycle is enabled.
                    // This ensures compatibility with other plugins that freeze time by setting this gamerule to false.
                    if (world.getGameRuleValue("doDaylightCycle")?.toBoolean() == true) {
                        val currentTime = world.time

                        // Calculate the desired tick increment based on the plugin's config
                        val desiredIncrement = if (currentTime in 0L..11999L) {
                            dayTickIncrement
                        } else {
                            nightTickIncrement
                        }

                        // The vanilla server adds 1 tick when doDaylightCycle is true.
                        // We need to add the difference to match our desired speed.
                        val adjustmentIncrement = desiredIncrement - 1.0

                        // Use an accumulator to handle fractional increments smoothly
                        val accumulator = timeAccumulators.getOrPut(world) { 0.0 } + adjustmentIncrement
                        val ticksToAdjust = accumulator.toLong()

                        if (ticksToAdjust != 0L) {
                            world.fullTime += ticksToAdjust
                        }

                        // Store the remainder for the next tick
                        timeAccumulators[world] = accumulator - ticksToAdjust
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L) // Run the task every single tick for smooth transition
    }
}
