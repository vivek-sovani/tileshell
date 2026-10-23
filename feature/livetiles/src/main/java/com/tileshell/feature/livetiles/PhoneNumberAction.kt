package com.tileshell.feature.livetiles

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Wraps a contact action button (call, message, whatsapp). With one number a
 * tap goes straight to [onNumber]; with several it opens a menu under the
 * button listing each number with its type, the contact's default first
 * ([orderPhoneNumbers]), and [onNumber] gets the one picked. With none the
 * tap does nothing.
 */
@Composable
fun PhoneNumberAction(
    numbers: List<PhoneEntry>,
    onNumber: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (onClick: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        content {
            when {
                numbers.size == 1 -> onNumber(numbers[0].number)
                numbers.size > 1 -> open = true
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            numbers.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(phoneEntryLine(entry)) },
                    onClick = {
                        open = false
                        onNumber(entry.number)
                    },
                )
            }
        }
    }
}

/** "98765 43210 · mobile · default" — the number, then its type and whether it is the default. */
fun phoneEntryLine(entry: PhoneEntry): String =
    listOfNotNull(entry.number, entry.label.ifBlank { null }, "default".takeIf { entry.isDefault }).joinToString(" · ")
