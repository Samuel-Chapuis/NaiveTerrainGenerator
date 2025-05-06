package Minecraft.util
// src/main/kotlin/LevelDatInspector.kt
import org.jglrxavpok.hephaistos.nbt.*
import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream

fun inspectLevelDat(levelDatPath: String) {
    val levelDatFile = File(levelDatPath)
    require(levelDatFile.exists() && levelDatFile.isFile) {
        "Fichier introuvable ou non valide : $levelDatPath"
    }

    // Lecture et décompression GZIP
    NBTReader(BufferedInputStream(FileInputStream(levelDatFile))).use { reader ->
        val root = reader.read() as? NBTCompound
            ?: throw IllegalArgumentException("Le root de level.dat n'est pas un Compound")
        // Parcours et affichage
        printCompound("", root)
    }
}

// Fonction récursive pour afficher un Compound
fun printCompound(indent: String, comp: NBTCompound) {
    for(entry in comp.entries) {
        val name = entry.key
        val tag  = entry.value
        println("$indent$name : ${tag::class.simpleName}")
        printTag("$indent  ", tag)
    }
}

// Fonction qui dispatch selon le type de tag
fun printTag(indent: String, tag: NBT) {
    when(tag) {
        is NBTCompound -> printCompound(indent, tag)
        is NBTList<*>   -> {
            println("$indent[List size=${tag.size}]")
            tag.forEachIndexed { i, element ->
                println("$indent  - #$i : ${element!!::class.simpleName}")
                printTag("$indent    ", element)
            }
        }
        is NBTByte      -> println("$indent${tag.getValue()}")
        is NBTShort     -> println("$indent${tag.getValue()}")
        is NBTInt       -> println("$indent${tag.getValue()}")
        is NBTLong      -> println("$indent${tag.getValue()}")
        is NBTFloat     -> println("$indent${tag.getValue()}")
        is NBTDouble    -> println("$indent${tag.getValue()}")
        is NBTString    -> println("$indent\"${tag.value}\"")
        is NBTByteArray -> println("$indent[ByteArray length=${tag.size}]")
        is NBTIntArray  -> println("$indent[IntArray length=${tag.size}]")
        is NBTLongArray -> println("$indent[LongArray length=${tag.size}]")
        else            -> println("$indent<unknown tag>")
    }
}
