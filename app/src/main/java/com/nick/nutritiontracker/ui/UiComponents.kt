package com.nick.nutritiontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Macro Colors
val ProteinGreen = Color(0xFF2E7D32)
val CarbOrange = Color(0xFFFF9800)
val SugarRed = Color(0xFFD32F2F)
val UnsaturatedYellow = Color(0xFFFBC02D)
val SaturatedGrey = Color(0xFF757575)

@Composable
fun MacroNumber(value: Double, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = "%.0f".format(value),
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.widthIn(min = 20.dp)
    )
}

@Composable
fun MacroSeparator() {
    Text(
        text = "|",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
fun CompactMacroRow(
    kcal: Double,
    protein: Double,
    complexCarbs: Double,
    sugar: Double,
    unsaturatedFat: Double,
    saturatedFat: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "%.0f".format(kcal),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(4.dp))
        MacroNumber(protein, ProteinGreen)
        MacroSeparator()
        MacroNumber(complexCarbs, CarbOrange)
        MacroNumber(sugar, SugarRed)
        MacroSeparator()
        MacroNumber(unsaturatedFat, UnsaturatedYellow)
        MacroNumber(saturatedFat, SaturatedGrey)
    }
}

@Composable
fun AutoSelectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value)
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = {
            textFieldValue = it
            if (it.text != value) {
                onValueChange(it.text)
            }
        },
        label = label,
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(150)
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(0, textFieldValue.text.length)
                    )
                }
            }
        },
        readOnly = readOnly,
        singleLine = singleLine,
        colors = colors,
        trailingIcon = trailingIcon,
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
