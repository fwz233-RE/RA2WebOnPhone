package com.ra2.webonphone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ra2.webonphone.data.SettingsRepository
import com.ra2.webonphone.ui.theme.RA2WebOnPhoneTheme
import com.ra2.webonphone.util.LocaleHelper

data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val description: String,
    val license: String,
    val url: String
)

class OpenSourceActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settingsRepo = SettingsRepository(newBase)
        val language = settingsRepo.getAppLanguage()
        super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
    }

    private val libraries = listOf(
        OpenSourceLibrary(
            name = "Jetpack Compose",
            author = "Google",
            description = "Android 现代声明式 UI 工具包",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/compose"
        ),
        OpenSourceLibrary(
            name = "Material Design 3",
            author = "Google",
            description = "Material Design 3 组件库",
            license = "Apache 2.0",
            url = "https://m3.material.io/"
        ),
        OpenSourceLibrary(
            name = "Coil",
            author = "Coil Contributors",
            description = "Kotlin 协程图片加载库",
            license = "Apache 2.0",
            url = "https://coil-kt.github.io/coil/"
        ),
        OpenSourceLibrary(
            name = "Kotlinx Serialization",
            author = "JetBrains",
            description = "Kotlin 多平台序列化库",
            license = "Apache 2.0",
            url = "https://github.com/Kotlin/kotlinx.serialization"
        ),
        OpenSourceLibrary(
            name = "AndroidX Core KTX",
            author = "Google",
            description = "Android Kotlin 扩展库",
            license = "Apache 2.0",
            url = "https://developer.android.com/kotlin/ktx"
        ),
        OpenSourceLibrary(
            name = "AndroidX Activity Compose",
            author = "Google",
            description = "Compose 与 Activity 集成库",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/activity"
        ),
        OpenSourceLibrary(
            name = "AndroidX Navigation Compose",
            author = "Google",
            description = "Compose 导航组件",
            license = "Apache 2.0",
            url = "https://developer.android.com/jetpack/compose/navigation"
        ),
        OpenSourceLibrary(
            name = "Material Icons Extended",
            author = "Google",
            description = "Material Design 扩展图标库",
            license = "Apache 2.0",
            url = "https://fonts.google.com/icons"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RA2WebOnPhoneTheme {
                OpenSourceScreen(
                    libraries = libraries,
                    onBackClick = { finish() },
                    onLibraryClick = { library ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(library.url))
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceScreen(
    libraries: List<OpenSourceLibrary>,
    onBackClick: () -> Unit,
    onLibraryClick: (OpenSourceLibrary) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.open_source_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(libraries) { library ->
                OpenSourceCard(
                    library = library,
                    onClick = { onLibraryClick(library) }
                )
            }
        }
    }
}

@Composable
fun OpenSourceCard(
    library: OpenSourceLibrary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = library.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = library.license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = library.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = library.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = stringResource(R.string.open_link),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
