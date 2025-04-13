
class Map (
    var minChunkX: Int = 0,
    var maxChunkX: Int = -1,  // initially no chunk generated
    var minChunkY: Int = 0,
    var maxChunkY: Int = -1,
    var height_map: Array<IntArray> = arrayOf(),
    var gradiant_map: Array<IntArray> = arrayOf(),
    var heat_map: Array<IntArray> = arrayOf()
)


