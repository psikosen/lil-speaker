package com.example.lilspeaker.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.lilspeaker.R

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = stringResource(id = R.string.composer_label)) },
            placeholder = { Text(text = stringResource(id = R.string.composer_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                onSend()
                focusManager.clearFocus()
            }),
            enabled = !sending
        )
        IconButton(
            onClick = {
                onSend()
                focusManager.clearFocus()
            },
            enabled = value.isNotBlank() && !sending
        ) {
            Icon(imageVector = Icons.Filled.Send, contentDescription = stringResource(id = R.string.send))
        }
    }
}
