package com.gaebriu.cyclo.ui.debug

import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TESTE TEMPORÁRIO (não faz parte da app): mostra se o aparelho desenha RenderEffect de desfoque.
 * Para o ver, na MainActivity troca o conteúdo por `BlurSelfTest()`.
 * Cada quadrado branco deve aparecer mais desfocado do que o anterior. Se algum ficar nítido
 * ou desaparecer, diz-me qual (e manda-me um print).
 */
@Composable
fun BlurSelfTest() {
    val context = LocalContext.current
    val text = TextStyle(color = Color.White, fontSize = 12.sp)

    val info = remember {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val animatorScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        listOf(
            "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Hardware: ${Build.HARDWARE} / ${Build.BOARD}",
            "isCrossWindowBlurEnabled: ${windowManager.isCrossWindowBlurEnabled}",
            "Escala de animações: $animatorScale",
        )
    }
    LaunchedEffect(Unit) { info.forEach { Log.i("CycloBlurTest", it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010101))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        info.forEach { BasicText(it, style = text) }
        BasicText("Cada quadrado deve estar mais desfocado do que o anterior:", style = text)

        listOf(0f, 2f, 5f, 10f, 25f, 50f, 130f).forEach { sigma ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("raio $sigma dp", style = text, modifier = Modifier.width(90.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            if (sigma > 0f) {
                                val px = sigma * density
                                renderEffect = BlurEffect(px, px, TileMode.Decal)
                            }
                            clip = false
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("Modifier.blur(10)", style = text, modifier = Modifier.width(90.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .blur(10.dp)
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
