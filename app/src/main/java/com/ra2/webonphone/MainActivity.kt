package com.ra2.webonphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ra2.webonphone.data.LinkItem
import com.ra2.webonphone.data.LinkRepository
import com.ra2.webonphone.ui.components.AddLinkDialog
import com.ra2.webonphone.ui.components.LinkCard
import com.ra2.webonphone.ui.theme.RA2WebOnPhoneTheme

class MainActivity : ComponentActivity() {

    private lateinit var linkRepository: LinkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        linkRepository = LinkRepository(this)

        enableEdgeToEdge()
        setContent {
            RA2WebOnPhoneTheme {
                MainScreen(
                    linkRepository = linkRepository,
                    onLinkClick = { link ->
                        openWebView(link)
                    }
                )
            }
        }
    }

    private fun openWebView(link: LinkItem) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_URL, link.url)
            putExtra(WebViewActivity.EXTRA_TITLE, link.title)
        }
        startActivity(intent)
    }
}

@Composable
fun MainScreen(
    linkRepository: LinkRepository,
    onLinkClick: (LinkItem) -> Unit
) {
    var links by remember { mutableStateOf(linkRepository.getLinks()) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加链接",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (links.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无链接，点击右下角添加",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(links, key = { it.id }) { link ->
                        LinkCard(
                            linkItem = link,
                            onClick = { onLinkClick(link) },
                            onDelete = if (!link.isDefault) {
                                {
                                    linkRepository.removeLink(link.id)
                                    links = linkRepository.getLinks()
                                }
                            } else null
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddLinkDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { title, url ->
                    linkRepository.addLink(title, url)
                    links = linkRepository.getLinks()
                    showAddDialog = false
                }
            )
        }
    }
}
