package app.backlit.pet

import app.backlit.pet.rigs.AxolotlRig
import app.backlit.pet.rigs.FrogRig
import app.backlit.pet.rigs.OwlRig
import app.backlit.pet.rigs.PenguinRig
import app.backlit.pet.rigs.RobotRig
import app.backlit.render.PixelGrid

/** Draws any pet: the ghost through its original art, the others through the shared rig system. */
object PetArt {
    fun rigFor(kind: PetKind): Rig? = when (kind) {
        PetKind.GHOST -> null
        PetKind.FROG -> FrogRig
        PetKind.PENGUIN -> PenguinRig
        PetKind.AXOLOTL -> AxolotlRig
        PetKind.OWL -> OwlRig
        PetKind.ROBOT -> RobotRig
    }

    fun frame(kind: PetKind, size: Int, pose: Pose, now: Long): PixelGrid =
        rigFor(kind)?.let { RigArt.frame(it, size, pose, now) } ?: GhostArt.frame(size, pose, now)

    fun still(kind: PetKind, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid =
        rigFor(kind)?.let { RigArt.still(it, size, pose, minuteOfHour) } ?: GhostArt.still(size, pose, minuteOfHour)
}
