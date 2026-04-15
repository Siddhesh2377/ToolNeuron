package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.global.Standards

// ── Password Text Field ──

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    showToggle: Boolean = true,
    showPasswordState: Boolean? = null,
    onToggleVisibility: (() -> Unit)? = null
) {
    var internalVisible by remember { mutableStateOf(false) }
    val isVisible = showPasswordState ?: internalVisible
    val onToggle = onToggleVisibility ?: { internalVisible = !internalVisible }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = { Icon(TnIcons.Lock, contentDescription = null) },
        trailingIcon = if (showToggle) {
            {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (isVisible) TnIcons.EyeOff else TnIcons.Eye,
                        contentDescription = if (isVisible) "Hide password" else "Show password"
                    )
                }
            }
        } else null,
        isError = isError,
        supportingText = supportingText,
        shape = RoundedCornerShape(Standards.RadiusLg)
    )
}
