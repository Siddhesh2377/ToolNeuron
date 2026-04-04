package com.dark.tool_neuron.ui.screens.home_screen

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenTopbar() {
    CenterAlignedTopAppBar(title = {
        Text("Qwen-1.5B-250M")
    })
}