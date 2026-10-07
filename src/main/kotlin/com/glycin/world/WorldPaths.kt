package com.glycin.world

import com.glycin.api.model.Cell
import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.file.Path
import java.nio.file.Paths

@Startup
@ApplicationScoped
class WorldPaths(
    @param:ConfigProperty(name = "world.repo.path", defaultValue = "./world")
    private val configuredPath: String,
) {

    val repo: Path = Paths.get(configuredPath).toAbsolutePath().normalize()
    val grid: Path = repo.resolve(GRID_FILENAME)
    val wallet: Path = repo.resolve(WALLET_FILENAME)
    val objects: Path = repo.resolve(OBJECTS_DIRNAME)
    val meta: Path = repo.resolve(META_DIRNAME)

    fun objectFile(row: Int, col: Int): Path = objects.resolve(objectFilename(row, col))

    companion object {
        const val GRID_FILENAME = "map.grid"
        const val WALLET_FILENAME = "wallet.res"
        const val OBJECTS_DIRNAME = "objects"
        const val META_DIRNAME = "meta"

        private val OBJECT_FILENAME_RE = Regex("""r(\d{4})_c(\d{4})\.json""")

        fun objectFilename(row: Int, col: Int): String = "r%04d_c%04d.json".format(row, col)
        fun objectRelativePath(row: Int, col: Int): String = "$OBJECTS_DIRNAME/${objectFilename(row, col)}"

        fun parseObjectPath(path: String): Cell? {
            if (!path.startsWith("$OBJECTS_DIRNAME/") || !path.endsWith(".json")) return null
            val match = OBJECT_FILENAME_RE.matchEntire(path.substringAfterLast('/')) ?: return null
            return Cell(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}
