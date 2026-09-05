package com.nathanb.lock.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.util.AppLanguage

@Composable
internal fun LanguageSelector(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    // Each language's own name is shown untranslated, so it stays recognizable no matter
    // which language the UI currently displays.
    val options = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.language_system),
        AppLanguage.ENGLISH to "English",
        AppLanguage.FRENCH to "Français",
        AppLanguage.GERMAN to "Deutsch",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { (language, label) ->
            val selected = currentLanguage == language
            val tintColor by animateColorAsState(
                targetValue = if (selected) colors.primary else colors.onSurface,
                animationSpec = tween(200),
                label = "languageOption",
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelected(language) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) colors.cardContainer else colors.surfaceContainerLow,
                border = if (selected) BorderStroke(2.dp, colors.primary.copy(alpha = 0.25f)) else null,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = tintColor,
                )
            }
        }
    }
}
