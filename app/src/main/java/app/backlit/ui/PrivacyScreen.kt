package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.R
import app.backlit.ui.components.PageHeader

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember {
        PolicyText.paragraphs(context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() })
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PageHeader("PRIVACY", onBack)
        paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}
