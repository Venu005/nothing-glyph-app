package app.backlit.glyph

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Nothing's own Glyph Toys manager (newer system versions only). */
object ToysManager {
    private fun intent() = Intent()
        .setComponent(ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canOpen(context: Context): Boolean = intent().resolveActivity(context.packageManager) != null

    fun open(context: Context): Boolean = try {
        context.startActivity(intent())
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
