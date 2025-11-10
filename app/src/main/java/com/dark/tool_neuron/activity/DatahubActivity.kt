package com.dark.tool_neuron.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.screens.hub.DataHubScreen
import com.dark.tool_neuron.ui.theme.NeuroVerseTheme
import com.dark.tool_neuron.ui.theme.rDP
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
                                startActivity(
                                    Intent(
                                        this@DatahubActivity,
                                        MainActivity::class.java
                                    )
                                )
                            }) {
                                Icon(Icons.Outlined.Home, "Home")
                            }
                        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
                    }) {
                    DataHubScreen(paddingValues = it)
                }
            }
        }
    }
}

