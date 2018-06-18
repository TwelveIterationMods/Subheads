package net.blay09.mods.twitchcrumbs;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.*;

@Mod(modid = "twitchcrumbs", name = "Twitchcrumbs", dependencies = "required-after:headcrumbs")
public class Twitchcrumbs {

	public static final Logger logger = LogManager.getLogger();

	@Mod.Instance
	public static Twitchcrumbs instance;

	private final List<String> whitelists = new ArrayList<>();
	private boolean autoReload;
	private int cacheTime;
	private int reloadInterval;
	private boolean firstTick = true;

	private String[] originalNames;
	private int tickTimer;

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		String[] sources = config.getStringList("sources", "general", new String[0], "One whitelist source link per line. Example: http://whitelist.twitchapps.com/list.php?id=12345");
		Collections.addAll(whitelists, sources);
		cacheTime = config.getInt("cacheTime", "general", 60 * 60 * 24, 0, Integer.MAX_VALUE, "How long should the cache be used until updates are pulled? (if autoReload is false) (in seconds)");
		autoReload = config.getBoolean("autoReload", "general", false, "Should the Twitchcrumbs automatically be reloaded in a specific interval? This will mean reading the remote file again and will reset Headcrumb's already-spawned list. The Creative Tab and NEI won't be updated until the game restarts, though.");
		reloadInterval = config.getInt("reloadInterval", "general", 60, 10, 60 * 12, "If autoReload is enabled, at what interval in minutes should the reload happen? (approximately, based on TPS)") * 60 * 20;
		config.save();

		FMLCommonHandler.instance().bus().register(this);
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandBase() {
			@Override
			public String getName() {
				return "twitchcrumbs";
			}

			@Override
			public String getUsage(ICommandSender sender) {
				return "/twitchcrumbs reload";
			}

			@Override
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				if (args.length != 1) {
					throw new WrongUsageException(getUsage(sender));
				}
				if (args[0].equals("reload")) {
					int registered = reloadTwitchCrumbs();
					sender.sendMessage(new TextComponentString("Reloaded Twitchcrumbs - registered " + registered + " users."));
					return;
				}
				throw new WrongUsageException(getUsage(sender));
			}

			@Override
			public int getRequiredPermissionLevel() {
				return 2;
			}

			@Override
			public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
				return args.length == 1 ? Collections.singletonList("reload"): super.getTabCompletions(server, sender, args, targetPos);
			}
		});
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (firstTick) { // We do this here instead of in init to forcefully skip Headcrumb's initialization code. Creating all the head stacks, adding all the dungeon loot and all that other stuff is too much for huge lists like SF2.5.
			firstTick = false;
			reloadTwitchCrumbs();
		}
		if (autoReload) {
			tickTimer++;
			if (tickTimer > reloadInterval) {
				tickTimer = 0;
				reloadTwitchCrumbs();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public int reloadTwitchCrumbs() {
		// Load the whitelist from all sources
		List<String> list = new ArrayList<>();
		for (String source : whitelists) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(CachedAPI.loadCachedAPI(source, source.replace(":", "_").replace("/", "_").replace("?", "_"), 1000 * cacheTime)))) {
				String line;
				while ((line = reader.readLine()) != null) {
					list.add(line);
				}
			} catch (IOException e) {
				logger.error("Failed to load whitelist from source {}: {}", source, e);
			}
		}

		logger.info("Registering {} Twitchcrumbs users...", list.size());
		// We don't use Headcrumb's IMC API because it's inefficient and requires mod-sent option to be enabled
		// Append our whitelist names to the "others" list in Headcrumbs instead
		try {
			Class headcrumbs = Class.forName("ganymedes01.headcrumbs.Headcrumbs");
			// ## Option was removed in 1.9.4:
//			if (list.size() > 50) { // Calm down now.
//				Field hidePlayerHeadsFromTabField = headcrumbs.getField("hidePlayerHeadsFromTab");
//				hidePlayerHeadsFromTabField.set(null, true);
//			}
			Field othersField = headcrumbs.getField("others");
			String[] others = (String[]) othersField.get(null);
			if (originalNames == null) {
				originalNames = new String[others.length];
				System.arraycopy(others, 0, originalNames, 0, others.length);
			} else {
				others = originalNames;
			}
			Collections.addAll(list, others);
			othersField.set(null, list.toArray(new String[list.size()]));

			// Clear EntityHuman's name cache to allow immediate spawning of new names in case of a reload
			Class entityHuman = Class.forName("ganymedes01.headcrumbs.entity.EntityHuman");
			Field namesField = entityHuman.getDeclaredField("names");
			namesField.setAccessible(true);
			List<String> names = (List<String>) namesField.get(null);
			names.clear();
		} catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException | ClassCastException e) {
			logger.error("Oops! Twitchcrumbs is not compatible with this version of Headcrumbs!", e);
		}
		return list.size();
	}

}
