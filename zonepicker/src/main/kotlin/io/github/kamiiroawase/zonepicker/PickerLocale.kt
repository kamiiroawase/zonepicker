package io.github.kamiiroawase.zonepicker

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** Locale decisions the picker makes for itself, shared by every entry point so the activity
 *  page, the dialog and the bottom sheet all agree: zone display-name inputs and the layout
 *  direction. */
internal object PickerLocale {
    /** Layout direction resolved from [locale] the way View.LAYOUT_DIRECTION_LOCALE resolved
     *  it for the old view tree: the picker mirrors in RTL locales on its own, even when the
     *  host app has not enabled RTL support.
     *
     *  The algorithm is the platform's own pre-ICU one (and the support library's fallback):
     *  the bidi category of the first char of the locale's display name in its own language.
     *  Pure JDK, so local unit tests run it too — the framework's TextUtils and its androidx
     *  twin both throw "not mocked" off-device. It agrees with the ICU answer for every
     *  locale whose display name opens with a letter of its own script, which covers all
     *  real-world RTL languages. */
    fun layoutDirection(locale: Locale): LayoutDirection {
        val name = locale.getDisplayName(locale)

        if (locale == Locale.ROOT || name.isEmpty()) return LayoutDirection.Ltr

        return when (Character.getDirectionality(name[0])) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> LayoutDirection.Rtl
            else -> LayoutDirection.Ltr
        }
    }

    /** Zone display-name inputs for the picker's own UI language, resolved against [context].
     *  Per-app locales (API 33+ framework LocaleManager) are applied to an activity's
     *  context, never to the application context, so callers pass their UI context. */
    fun zoneNames(context: Context): ZonePickerViewModel.ZoneNames = ZonePickerViewModel.ZoneNames(
        nameLocale = nameLocale(context),
        overrides =
        mapOf(
            "Asia/Taipei" to context.getString(R.string.zp_taipei_name),
            "Asia/Hong_Kong" to context.getString(R.string.zp_hong_kong_name),
            "Asia/Macau" to context.getString(R.string.zp_macau_name),
        ),
    )

    /** Chinese while the picker's own strings are the untranslated defaults; otherwise the
     *  context's locale, which reflects both the system and any per-app locale the platform
     *  applies there. The baseline resolves the title under Locale.ROOT — the default
     *  values/ bucket, which not even a host overriding values-zh can shift — so only a real
     *  localization (values-<locale> resources or a per-app locale) flips the decision. */
    private fun nameLocale(context: Context): Locale {
        val appContext = context.applicationContext

        val defaults =
            Configuration(appContext.resources.configuration).apply { setLocale(Locale.ROOT) }

        return if (appContext.createConfigurationContext(defaults).getString(R.string.zp_title) == context.getString(R.string.zp_title)) {
            Locale.SIMPLIFIED_CHINESE
        } else {
            context.resources.configuration.locales[0]
        }
    }
}
