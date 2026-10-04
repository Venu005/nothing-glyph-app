package app.backlit.render.charge

import app.backlit.render.PixelGrid

/** One charging look. level is 0..100; times are ms since the moment started. */
interface ChargeStyle {
    val id: String
    val label: String
    /** Resting display (not charging, and always in AOD). No motion. */
    fun still(size: Int, level: Int): PixelGrid
    /** 0..5000 ms after plugging in; ends with the % reveal (except Big number). */
    fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid
    /** Gentle loop while charging. */
    fun charging(size: Int, level: Int, tMs: Long): PixelGrid
    /** 0..3500 ms when the target is reached. */
    fun done(size: Int, tMs: Long): PixelGrid
}
