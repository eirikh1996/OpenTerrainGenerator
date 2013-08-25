package com.khorn.terraincontrol.configuration;

import com.khorn.terraincontrol.LocalBiome;
import com.khorn.terraincontrol.LocalWorld;
import com.khorn.terraincontrol.TerrainControl;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.logging.Level;

/**
 *
 */
public class BiomeConfigManager
{

    private final File worldBiomesDir;
    private final File globalBiomesDir;
    private int biomesCount; // Overall biome count in this world.
    private byte[] ReplaceMatrixBiomes = new byte[256];
    private LocalWorld world;
    private WorldConfig worldConfig;
    private boolean checkOnly;
    private String LoadedBiomeNames = "";

    public BiomeConfigManager(File settingsDir, LocalWorld world, WorldConfig wConfig, HashMap<String, Integer> customBiomes, boolean checkOnly)
    {

        this.world = world;
        this.worldConfig = wConfig;
        this.checkOnly = checkOnly;

        // Check biome ids, These are the names from the worldConfig file
        for (String biomeName : customBiomes.keySet())
            if (customBiomes.get(biomeName) == -1)
                customBiomes.put(biomeName, world.getFreeBiomeId());

        this.worldBiomesDir = new File(settingsDir, TCDefaultValues.WorldBiomeConfigDirectoryName.stringValue());
        this.globalBiomesDir = new File(TerrainControl.getEngine().getTCDataFolder(), TCDefaultValues.GlobalBiomeConfigDirectoryName.stringValue());


        if (!globalBiomesDir.exists())
        {
            if (!globalBiomesDir.mkdir())
            {
                TerrainControl.log(Level.WARNING, "Error creating biome configs directory, working with defaults");
                return;
            }
        }

        if (!worldBiomesDir.exists())
        {
            if (!worldBiomesDir.mkdir())
            {
                TerrainControl.log(Level.WARNING, "Error creating biome configs directory, working with defaults");
                return;
            }
        }

        // Build biome replace matrix
        for (int i = 0; i < this.ReplaceMatrixBiomes.length; i++)
            this.ReplaceMatrixBiomes[i] = (byte) i;

        //>>	Init the biomeConfigs Array
        this.worldConfig.biomeConfigs = new BiomeConfig[world.getMaxBiomesCount()];
        //>>	Set variable for biomeCount, MIGHT NOT NEED
        this.biomesCount = 0;

        //>>	This arrayList now contains all biomes listed in `DefaultBiome`
        loadBiomes(new ArrayList<LocalBiome>(world.getDefaultBiomes()), globalBiomesDir);

        ArrayList<LocalBiome> localBiomes = new ArrayList<LocalBiome>(customBiomes.size());
        //>>	This adds all custombiomes that have been listed in WorldConfig to the arrayList
        for (Iterator<Entry<String, Integer>> it = customBiomes.entrySet().iterator(); it.hasNext();)
        {
            Entry<String, Integer> entry = it.next();
            if (checkOnly)
                localBiomes.add(world.getNullBiome(entry.getKey()));
            else
                localBiomes.add(world.AddBiome(entry.getKey(), entry.getValue()));
        }
        loadBiomes(localBiomes, worldBiomesDir);

        TerrainControl.log(Level.INFO, "Loaded {0} biomes", new Object[]
        {
            biomesCount
        });
        TerrainControl.logIfLevel(Level.ALL, Level.CONFIG, LoadedBiomeNames);

    }

    private void loadBiomes(ArrayList<LocalBiome> biomesToLoad, File biomeFolder)
    {

        for (LocalBiome localBiome : biomesToLoad)
        {
            BiomeConfig config = new BiomeConfig(biomeFolder, localBiome, this.worldConfig);

            //t>>	Should this be before or after inheritance checks?
            if (this.checkOnly)
                continue;

            if (config.settingsCache.containsKey(TCDefaultValues.BiomeExtends.name()))
            {
                /* Inheritance (Are we gonna need a BiomeLoader?? It would
                 * probably clean the code up a bit...)
                 * - Grab the settingsCache value for BiomeExtends
                 * - Get the id of the biome to be extended and find it in
                 * biomeConfigs
                 * - if not in biomeConfigs, pre-load it?
                 * - merge the two biomeConfig's
                 */
                String biomeToExtend = config.settingsCache.get(TCDefaultValues.BiomeExtends.name());
            }


            if (!config.ReplaceBiomeName.equals(""))
            {
                this.worldConfig.HaveBiomeReplace = true;
                this.ReplaceMatrixBiomes[config.Biome.getId()] = (byte) world.getBiomeIdByName(config.ReplaceBiomeName);
            }

            if (this.worldConfig.NormalBiomes.contains(config.name))
                this.worldConfig.normalBiomesRarity += config.BiomeRarity;
            if (this.worldConfig.IceBiomes.contains(config.name))
                this.worldConfig.iceBiomesRarity += config.BiomeRarity;

            if (!this.worldConfig.BiomeConfigsHaveReplacement)
                this.worldConfig.BiomeConfigsHaveReplacement = config.ReplaceCount > 0;
            if (this.biomesCount != 0)
                LoadedBiomeNames += ", ";
            LoadedBiomeNames += localBiome.getName();
            // Add biome to the biome array
            if (this.worldConfig.biomeConfigs[localBiome.getId()] == null)
            {
                // Only if it won't overwrite another biome in the array
                this.biomesCount++;
            } else
            {
                TerrainControl.log(Level.WARNING, "Duplicate biome id " + localBiome.getId() + " (" + this.worldConfig.biomeConfigs[localBiome.getId()].name + " and " + config.name + ")!");
            }
            this.worldConfig.biomeConfigs[localBiome.getId()] = config;

            if (this.worldConfig.biomeMode == TerrainControl.getBiomeModeManager().FROM_IMAGE)
            {
                if (this.worldConfig.biomeColorMap == null)
                    this.worldConfig.biomeColorMap = new HashMap<Integer, Integer>();

                try
                {
                    int color = Integer.decode(config.BiomeColor);
                    if (color <= 0xFFFFFF)
                        this.worldConfig.biomeColorMap.put(color, config.Biome.getId());
                } catch (NumberFormatException ex)
                {
                    TerrainControl.log(Level.WARNING, "Wrong color in " + config.Biome.getName());
                }

            }
        }
    }

    public static BiomeConfig merge(BiomeConfig baseBiome, BiomeConfig extendingBiome)
    {
        for (String key : baseBiome.settingsCache.keySet())
        {
            if (!extendingBiome.settingsCache.containsKey(key))
            {
                extendingBiome.settingsCache.put(key, baseBiome.settingsCache.get(key));
            }
        }
        return extendingBiome;
    }

}