package app.backlit.render.faces

import app.backlit.render.FaceContext
import app.backlit.render.PixelGrid

interface Face {
    val id: String
    val label: String
    fun render(ctx: FaceContext): PixelGrid
    fun needsSecondTicks(ctx: FaceContext): Boolean
}

object Faces {
    val all: List<Face> = listOf(AnalogFace)

    fun byId(id: String): Face = all.firstOrNull { it.id == id } ?: all.first()

    fun next(id: String): Face {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}
