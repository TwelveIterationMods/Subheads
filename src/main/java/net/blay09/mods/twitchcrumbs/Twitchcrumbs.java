package net.blay09.mods.twitchcrumbs;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mod(modid = "twitchcrumbs", name = "Twitchcrumbs", dependencies = "required-after:headcrumbs")
public class Twitchcrumbs {

    private static final Logger logger = LogManager.getLogger();

    @Mod.Instance
    public static Twitchcrumbs instance;

    private final List<String> whitelists = new ArrayList<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        String[] sources = config.getStringList("sources", "general", new String[0], "One whitelist source link per line. Example: http://whitelist.twitchapps.com/list.php?id=12345");
        Collections.addAll(whitelists, sources);
        config.save();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
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
            Collections.addAll(list, others);
            othersField.set(null, list.toArray(new String[list.size()]));
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            logger.error("Oops! Twitchcrumbs is not compatible with this version of Headcrumbs!", e);
        }
    }

}
