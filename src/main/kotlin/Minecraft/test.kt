package Minecraft

import org.jglrxavpok.hephaistos.data.RandomAccessFileSource
import org.jglrxavpok.hephaistos.mca.BlockState
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.*
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile

fun mapWorkshop() {
    // 1. Prépare le dossier world "ma_carte_personalisé" et le sous-dossier "region"
    val worldDir = File("ma_carte_personalisé")
    val regionDir = File(worldDir, "region")
    if (!regionDir.exists()) regionDir.mkdirs()

    // 2. Génére r.0.0.mca dans world/region
    val regionFile = File(regionDir, "r.0.0.mca")
    RandomAccessFileSource(RandomAccessFile(regionFile, "rw")).use { dataSource ->
        val region = RegionFile(dataSource, 0, 0)

        // 3. Plateau plat
        val air = BlockState("minecraft:air")
        for (x in 0 until 16) for (z in 0 until 16) {
            region.setBlockState(x, 0, z, BlockState("minecraft:bedrock"))
            region.setBlockState(x, 1, z, BlockState("minecraft:dirt"))
            region.setBlockState(x, 2, z, BlockState("minecraft:dirt"))
            region.setBlockState(x, 3, z, BlockState("minecraft:grass_block"))
            for (y in 4 until 256) region.setBlockState(x, y, z, air)
        }

        // 4. Lecture de struct.nbt depuis classpath
        val resource = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("structures/struct.nbt")
            ?: error("structures/struct.nbt introuvable")
        val reader = NBTReader(BufferedInputStream(resource))
        val rootTag = reader.read() as NBTCompound

        // 5. Extraction des listes blocks & palette
        val blocksList: NBTList<NBTCompound> =
            rootTag.getList("blocks") ?: error("blocks manquante")
        val paletteList: NBTList<NBTCompound> =
            rootTag.getList("palette") ?: error("palette manquante")

        // 6. Placement périodique de la structure
        val spacingX = 64
        val spacingZ = 64
        for (baseX in 0 until (32 * 16) step spacingX) {
            for (baseZ in 0 until (32 * 16) step spacingZ) {
                for (entry in blocksList.asListView()) {
                    val posList: NBTList<NBTInt> =
                        entry.getList("pos") ?: error("pos manquante")
                    val coords = posList.asListView()
                    val localX = coords[0].getValue().toInt()
                    val localY = coords[1].getValue().toInt()
                    val localZ = coords[2].getValue().toInt()

                    val idx = entry.getInt("state") ?: error("state manquant")
                    val stateTag = paletteList.asListView().getOrNull(idx)
                        ?: error("index palette invalide")
                    val blockName = stateTag.getString("Name")
                        ?: error("Name manquante")

                    region.setBlockState(
                        localX + (baseX % 16),
                        localY,
                        localZ + (baseZ % 16),
                        BlockState(blockName)
                    )
                }
            }
        }

        // 7. Sauvegarde de la région
        region.flushCachedChunks()
    }

    // 8. Écriture d’un level.dat minimal dans worldDir
    val levelDat: NBTCompound = NBT.Compound { root ->
        root.put("Data", NBT.Compound { data ->
            // utilisez ici les classes NBTInt, NBTLong, NBTString
            data.put("DataVersion", NBTInt(2865))
            data.put("LevelName", NBTString("ma_carte_personalisé"))
            data.put("RandomSeed", NBTLong(System.currentTimeMillis()))
            data.put("generatorName", NBTString("flat"))
        })
    }

    File(worldDir, "level.dat").outputStream().use { os ->
        NBTWriter(os).use { writer ->
            // écrit le compound racine sans nom
            writer.writeNamed("", levelDat)
        }

    }
}