package com.trevio.android.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.Member

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemizedSplitEditor(
    members: List<Member>,
    currencySymbol: String,
    itemizedData: ItemizedSplitData,
    onItemizedDataChange: (ItemizedSplitData) -> Unit
) {
    val activeMembers = members.filter { it.status == "active" }

    fun addItem() {
        val newItem = BillItem(
            itemId = "item_${System.currentTimeMillis()}_${(0..9999).random()}",
            name = "",
            amount = 0.0,
            assignedTo = emptyList()
        )
        onItemizedDataChange(itemizedData.copy(items = itemizedData.items + newItem))
    }

    fun removeItem(itemId: String) {
        onItemizedDataChange(itemizedData.copy(items = itemizedData.items.filter { it.itemId != itemId }))
    }

    fun updateItem(itemId: String, updated: BillItem) {
        onItemizedDataChange(
            itemizedData.copy(
                items = itemizedData.items.map { if (it.itemId == itemId) updated else it }
            )
        )
    }

    fun toggleMember(itemId: String, uid: String) {
        val item = itemizedData.items.find { it.itemId == itemId } ?: return
        val newAssigned = if (item.assignedTo.contains(uid)) {
            item.assignedTo - uid
        } else {
            item.assignedTo + uid
        }
        updateItem(itemId, item.copy(assignedTo = newAssigned))
    }

    fun assignAll(itemId: String) {
        val item = itemizedData.items.find { it.itemId != itemId } ?: return
        val target = itemizedData.items.find { it.itemId == itemId } ?: return
        updateItem(itemId, target.copy(assignedTo = activeMembers.map { it.uid }))
    }

    fun duplicateItem(itemId: String) {
        val item = itemizedData.items.find { it.itemId == itemId } ?: return
        val newItem = item.copy(
            itemId = "item_${System.currentTimeMillis()}_${(0..9999).random()}",
            name = "${item.name} (copy)"
        )
        onItemizedDataChange(itemizedData.copy(items = itemizedData.items + newItem))
    }

    val itemsTotal = itemizedData.items.filter { it.assignedTo.isNotEmpty() }.sumOf { it.amount }
    val taxAmt = itemizedData.taxAmount
    val tipAmt = itemizedData.tipAmount
    val grandTotal = if (itemsTotal > 0) itemsTotal + taxAmt + tipAmt else 0.0

    val memberTotals = remember(itemizedData, activeMembers) {
        val totals = mutableMapOf<String, Double>()
        activeMembers.forEach { totals[it.uid] = 0.0 }

        for (item in itemizedData.items) {
            if (item.assignedTo.isEmpty()) continue
            val perPerson = item.amount / item.assignedTo.size
            for (uid in item.assignedTo) {
                totals[uid] = (totals[uid] ?: 0.0) + perPerson
            }
        }

        if (taxAmt > 0 && itemsTotal > 0) {
            if (itemizedData.taxSplitMode == "proportional") {
                for (m in activeMembers) {
                    totals[m.uid] = (totals[m.uid] ?: 0.0) + ((totals[m.uid] ?: 0.0) / itemsTotal) * taxAmt
                }
            } else {
                val membersWithItems = activeMembers.filter { (totals[it.uid] ?: 0.0) > 0.0 }
                val perPerson = taxAmt / maxOf(membersWithItems.size, 1)
                for (m in membersWithItems) {
                    totals[m.uid] = (totals[m.uid] ?: 0.0) + perPerson
                }
            }
        }

        if (tipAmt > 0 && itemsTotal > 0) {
            val baseForProp = itemsTotal + taxAmt
            if (itemizedData.tipSplitMode == "proportional" && baseForProp > 0) {
                for (m in activeMembers) {
                    totals[m.uid] = (totals[m.uid] ?: 0.0) + ((totals[m.uid] ?: 0.0) / baseForProp) * tipAmt
                }
            } else {
                val membersWithItems = activeMembers.filter { (totals[it.uid] ?: 0.0) > 0.0 }
                val perPerson = tipAmt / maxOf(membersWithItems.size, 1)
                for (m in membersWithItems) {
                    totals[m.uid] = (totals[m.uid] ?: 0.0) + perPerson
                }
            }
        }

        totals
    }

    val isValid = itemizedData.items.isNotEmpty() &&
        itemizedData.items.all { it.name.isNotBlank() && it.amount > 0.0 && it.assignedTo.isNotEmpty() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Items list
        if (itemizedData.items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No items added yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tap \"Add Item\" to start splitting your bill item by item.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        itemizedData.items.forEachIndexed { index, item ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Item header: number, name, amount, actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { updateItem(item.itemId, item.copy(name = it)) },
                            placeholder = { Text("Item ${index + 1} name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = if (item.amount > 0.0) String.format("%.2f", item.amount) else "",
                            onValueChange = { v ->
                                val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                updateItem(item.itemId, item.copy(amount = parsed))
                            },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(8.dp),
                            prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        IconButton(onClick = { duplicateItem(item.itemId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { removeItem(item.itemId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Member assignment
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { assignAll(item.itemId) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("All", style = MaterialTheme.typography.labelSmall)
                        }
                        activeMembers.forEach { member ->
                            val isAssigned = item.assignedTo.contains(member.uid)
                            FilterChip(
                                selected = isAssigned,
                                onClick = { toggleMember(item.itemId, member.uid) },
                                label = {
                                    val name = member.displayName.split(" ").firstOrNull() ?: ""
                                    Text(name, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }

                    // Per-person amount for this item
                    if (item.assignedTo.isNotEmpty() && item.amount > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val perPerson = item.amount / item.assignedTo.size
                        Text(
                            "$currencySymbol${String.format("%.2f", perPerson)} each" +
                                if (item.assignedTo.size > 1) " × ${item.assignedTo.size} people" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Add Item button
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { addItem() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Item")
        }

        // Tax & Tip
        if (itemizedData.items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tax
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tax", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = if (taxAmt > 0.0) String.format("%.2f", taxAmt) else "",
                                onValueChange = { v ->
                                    val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                    onItemizedDataChange(itemizedData.copy(taxAmount = parsed))
                                },
                                placeholder = { Text("0.00") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(8.dp),
                                prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = itemizedData.taxSplitMode == "proportional",
                                    onClick = { onItemizedDataChange(itemizedData.copy(taxSplitMode = "proportional")) },
                                    label = { Text("Prop", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = itemizedData.taxSplitMode == "equal",
                                    onClick = { onItemizedDataChange(itemizedData.copy(taxSplitMode = "equal")) },
                                    label = { Text("Equal", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        // Tip
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = if (tipAmt > 0.0) String.format("%.2f", tipAmt) else "",
                                onValueChange = { v ->
                                    val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                    onItemizedDataChange(itemizedData.copy(tipAmount = parsed))
                                },
                                placeholder = { Text("0.00") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(8.dp),
                                prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = itemizedData.tipSplitMode == "proportional",
                                    onClick = { onItemizedDataChange(itemizedData.copy(tipSplitMode = "proportional")) },
                                    label = { Text("Prop", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = itemizedData.tipSplitMode == "equal",
                                    onClick = { onItemizedDataChange(itemizedData.copy(tipSplitMode = "equal")) },
                                    label = { Text("Equal", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }

            // Summary
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Items total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol${String.format("%.2f", itemsTotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (taxAmt > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tax", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol${String.format("%.2f", taxAmt)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (tipAmt > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tip", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol${String.format("%.2f", tipAmt)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("$currencySymbol${String.format("%.2f", grandTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Per-member breakdown
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Per person breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    activeMembers.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(member.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$currencySymbol${String.format("%.2f", memberTotals[member.uid] ?: 0.0)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if ((memberTotals[member.uid] ?: 0.0) > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        if (!isValid && itemizedData.items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Each item needs a name, amount, and at least one person assigned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
