package com.gaebriu.cyclo.ui.i18n

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.gaebriu.cyclo.R
import java.util.Locale

/**
 * As línguas do Cyclo. Os textos estão nos recursos (values = inglês, values-pt = português de
 * Portugal); para juntar uma língua: uma pasta values-xx com as strings (incluindo "locale_tag"),
 * uma entrada aqui e uma em res/xml/locales_config.xml.
 *
 * A escolha é a língua por app do Android ([LocaleManager], Android 13+): o sistema guarda-a entre
 * sessões (e nas definições do Android, em Línguas da app), e a app muda logo, sem reiniciar (a
 * MainActivity trata da mudança de configuração; o Compose volta a ler os textos).
 *
 * @property tag a etiqueta BCP 47 que se pede ao sistema.
 * @property label o nome da língua no menu (action-menu-lang, Componentes 4).
 */
enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    English("en", R.string.language_english),
    Portuguese("pt-PT", R.string.language_portuguese);

    companion object {
        /** A língua dos textos que a app está a mostrar (a escolhida ou, sem escolha, a do sistema que existe). */
        fun current(context: Context): AppLanguage = fromTag(context.getString(R.string.locale_tag))

        fun fromTag(tag: String): AppLanguage {
            val language = Locale.forLanguageTag(tag).language
            return entries.firstOrNull { Locale.forLanguageTag(it.tag).language == language } ?: English
        }

        /** Muda a língua da app (guarda-se no sistema). */
        fun set(context: Context, language: AppLanguage) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(language.tag)
        }
    }
}

/**
 * A locale para datas e horas: a dos textos que se mostram (não a do sistema), para não aparecer
 * "segunda-feira" no meio de uma app em inglês (ou o contrário). Usada pela app, pelo widget e pela
 * notificação.
 */
fun Context.appLocale(): Locale = Locale.forLanguageTag(getString(R.string.locale_tag))

/** [appLocale] no Compose: muda quando a língua muda. */
@Composable
fun rememberAppLocale(): Locale {
    val tag = stringResource(R.string.locale_tag)
    val configuration = LocalConfiguration.current
    return remember(tag, configuration) { Locale.forLanguageTag(tag) }
}
