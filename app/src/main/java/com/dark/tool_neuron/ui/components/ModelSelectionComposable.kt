package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.Dimens
import com.dark.tool_neuron.ui.theme.LocalDimens


@Preview
@Composable
fun Screen(){
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ModelSelectionComposable()
    }
}


@Composable
fun ModelSelectionComposable() {
    val dimens = LocalDimens.current
    
    StandardCard(Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)) {


        
    }
}

@Composable
fun NoModelError(dimens: Dimens){
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacingSm)
            .padding(horizontal = dimens.spacingMd, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
    ) {
        Icon(
            imageVector = TnIcons.AlertTriangle,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            "No models installed. Download one from the store or load a local GGUF file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}