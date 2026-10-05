package app.backlit.pet

enum class PetKind(val id: String, val defaultName: String) {
    GHOST("ghost", "Boo"),
    FROG("frog", "Ribbit"),
    PENGUIN("penguin", "Waddles"),
    AXOLOTL("axolotl", "Lotl"),
    OWL("owl", "Hoot"),
    ROBOT("robot", "Bolt");

    companion object {
        fun byId(id: String): PetKind = entries.firstOrNull { it.id == id } ?: GHOST
    }
}
