package net.spaceeye.vmod.toolgun.modes.hud

import net.spaceeye.vmod.toolgun.modes.state.HydraulicsMode
import net.spaceeye.vmod.toolgun.modes.util.SimpleHUD
import net.spaceeye.vmod.toolgun.modes.util.ThreeClicksActivationSteps.*
import net.spaceeye.vmod.translate.*

interface HydraulicsHUD: SimpleHUD {
    override fun makeSubText(makeText: (String) -> Unit) {
        this as HydraulicsMode
        when {
            paStage == FIRST_RAYCAST && !primaryFirstRaycast -> makeText(COMMON_HUD_1.get())
            primaryFirstRaycast -> makeText(COMMON_HUD_2.get())
            paStage == SECOND_RAYCAST -> makeText(COMMON_HUD_3.get())
            paStage == FINALIZATION -> makeText(COMMON_HUD_4.get())
        }
    }
}