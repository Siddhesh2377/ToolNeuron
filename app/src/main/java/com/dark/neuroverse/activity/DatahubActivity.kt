package com.dark.neuroverse.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.R
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.viewModel.DataPackScreenViewModel
import com.google.firebase.FirebaseApp

class DatahubActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    rDP(10.dp)
                                )
                            ) {
                                Icon(
                                    painterResource(R.drawable.database_zap),
                                    contentDescription = null
                                )
                                Text("Data Packs")
                            }
                        }, actions = {
                            IconButton(onClick = {
                                startActivity(Intent(this@DatahubActivity, MainActivity::class.java))
                            }) {
                                Icon(Icons.Outlined.Home, "Home")
                            }
                        })
                    }) {
                    DataPackScreen(paddingValues = it)
                }
            }
        }
    }
}

@Composable
fun DataPackScreen(
    viewModel: DataPackScreenViewModel = viewModel(), paddingValues: PaddingValues
) {
    val packs by viewModel.packs.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadDataPacks()
    }

    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = rDP(12.dp), vertical = rDP(8.dp)),
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        items(packs) { packState ->
            val pack = packState.dataPack

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
                ) {
                    // Title & Description
                    Text(text = pack.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = pack.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Metadata Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDP(16.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦 ${pack.size}", style = MaterialTheme.typography.labelMedium)
                        Text("✍️ ${pack.author}", style = MaterialTheme.typography.labelMedium)
                        Text("📅 ${pack.issued}", style = MaterialTheme.typography.labelMedium)
                    }

                    // Spacer
                    Spacer(modifier = Modifier.height(rDP(6.dp)))

                    // Progress or Actions
                    Crossfade(
                        targetState = when {
                            packState.isDownloading -> "downloading"
                            packState.isInstalled -> "installed"
                            else -> "idle"
                        }, label = "PackActionCrossfade"
                    ) { state ->
                        when (state) {
                            "downloading" -> {
                                Column {
                                    LinearProgressIndicator(
                                        progress = { packState.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${(packState.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }

                            "installed" -> {
                                OutlinedButton(
                                    onClick = { viewModel.deleteDataPack(pack, context) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.onError,
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Delete Data Pack")
                                    Spacer(Modifier.width(rDP(10.dp)))
                                    Icon(Icons.TwoTone.Delete, "Delete")
                                }
                            }

                            else -> {
                                FilledTonalButton(
                                    onClick = { viewModel.installDataPack(pack, context) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Download & Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
