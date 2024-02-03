package net.spaceeye.vsource

import dev.architectury.platform.Platform
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

fun LOG(s: String) = VS.logger.warn(s)

object VS {
    const val MOD_ID = "vsource"
    val logger: Logger = LogManager.getLogger(MOD_ID)!!

    @JvmStatic
    fun init() {
        if (!Platform.isModLoaded("valkyrienskies")) {
            LOG("VALKYRIEN SKIES IS NOT INSTALLED. EXITING.")
            return
        }

        VSItems.register()
    }
}