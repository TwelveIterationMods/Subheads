package net.blay09.mods.twitchcrumbs;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

@Mod(modid = "twitchcrumbs", name = "Twitchcrumbs", dependencies = "required-after:headcrumbs")
public class Twitchcrumbs {

    private static final Logger logger = LogManager.getLogger();

    @Mod.Instance
    public static Twitchcrumbs instance;

    private final List<String> whitelists = new ArrayList<>();
    private boolean autoReload;
    private boolean fixSpecialMobsSupport;
    private int reloadInterval;

    private String[] originalNames;
    private int tickTimer;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        String[] sources = config.getStringList("sources", "general", new String[0], "One whitelist source link per line. Example: http://whitelist.twitchapps.com/list.php?id=12345");
        Collections.addAll(whitelists, sources);
        fixSpecialMobsSupport = config.getBoolean("fixSpecialMobsSupport", "general", false, "SpecialMobs can cause Headcrumbs mobs not to spawn due to missing support in Headcrumbs. Setting this to true will fix that issue. You probably want to disable this once Headcrumbs has fixed the issue on their side.");
        autoReload = config.getBoolean("autoReload", "general", false, "Should the Twitchcrumbs automatically be reloaded in a specific interval? This will mean reading the remote file again and will reset Headcrumb's already-spawned list. The Creative Tab and NEI won't be updated until the game restarts, though.");
        reloadInterval = config.getInt("reloadInterval", "general", 60, 10, 60 * 12, "If autoReload is enabled, at what interval in minutes should the reload happen? (approximately, based oof TPS)") * 60 * 20;
        config.save();

        if(autoReload) {
            FMLCommonHandler.instance().bus().register(this);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        reloadTwitchCrumbs();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        fixSpecialMobsSupport();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "twitchcrumbs";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/twitchcrumbs reload";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if(args.length != 1) {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
                if(args[0].equals("reload")) {
                    int registered = reloadTwitchCrumbs();
                    sender.addChatMessage(new ChatComponentText("Reloaded Twitchcrumbs - registered " + registered + " users."));
                    return;
                }
                throw new WrongUsageException(getCommandUsage(sender));
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 2;
            }
        });
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        tickTimer++;
        if(tickTimer > reloadInterval) {
            reloadTwitchCrumbs();
            tickTimer = 0;
        }
    }

    @SuppressWarnings("unchecked")
    public void fixSpecialMobsSupport() {
        if(!Loader.isModLoaded("SpecialMobs")) {
            return;
        }
        try {
            Class specialZombie = Class.forName("toast.specialMobs.entity.zombie.Entity_SpecialZombie");
            Class headcrumbs = Class.forName("ganymedes01.headcrumbs.Headcrumbs");
            if (headcrumbs.getField("enableHumanMobs").getBoolean(null)) {
                List<BiomeDictionary.Type> blacklistedBiomes = Arrays.asList(BiomeDictionary.Type.MUSHROOM);
                List<String> blacklistedBiomeNames = Arrays.asList("Tainted Land");

                List<BiomeGenBase> biomes = new LinkedList<>();
                biomeLoop: for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
                    if (biome != null) {
                        if (blacklistedBiomeNames.contains(biome.biomeName)) {
                            continue;
                        }

                        for (BiomeDictionary.Type type : BiomeDictionary.getTypesForBiome(biome)) {
                            if (blacklistedBiomes.contains(type)) {
                                continue biomeLoop;
                            }
                        }

                        for (Object obj : biome.getSpawnableList(EnumCreatureType.monster)) {
                            if (obj instanceof BiomeGenBase.SpawnListEntry) {
                                BiomeGenBase.SpawnListEntry entry = (BiomeGenBase.SpawnListEntry) obj;
                                if (entry.entityClass == specialZombie) {
                                    biomes.add(biome);
                                    continue biomeLoop;
                                }
                            }
                        }
                    }
                }

                int celebrityProb = headcrumbs.getField("celebrityProb").getInt(null);
                int celebrityMin = headcrumbs.getField("celebrityMin").getInt(null);
                int celebrityMax = headcrumbs.getField("celebrityMax").getInt(null);
                EntityRegistry.addSpawn((Class<? extends EntityLiving>) Class.forName("ganymedes01.headcrumbs.entity.EntityHuman"), celebrityProb, celebrityMin, celebrityMax, EnumCreatureType.monster, biomes.toArray(new BiomeGenBase[biomes.size()]));
            }
        } catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
            logger.error("Oops! Twitchcrumbs is not compatible with this version of Headcrumbs or SpecialMobs! Can't fix the spawning rules.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public int reloadTwitchCrumbs() {
        // Load the whitelist from all sources
        List<String> list = new ArrayList<>();
        for(String source : whitelists) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(source).openStream()))) {
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
            Field othersField = headcrumbs.getField("others");
            String[] others = (String[]) othersField.get(null);
            if(originalNames == null) {
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
