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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trevio.android.domain.model.BillItem
import com.trevio.android.domain.model.ItemizedSplitData
import com.trevio.android.domain.model.Member
import com.trevio.android.util.MemberStatus
import com.trevio.android.R
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemizedSplitEditor(
    members: List<Member>,
    currencySymbol: String,
    itemizedData: ItemizedSplitData,
    onItemizedDataChange: (ItemizedSplitData) -> Unit,
    expenseAmount: Double = 0.0
) {
    val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }

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

    val totalMatchesExpense = expenseAmount <= 0.0 || kotlin.math.abs(grandTotal - expenseAmount) <= 0.01

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
                    Text(stringResource(R.string.itemized_no_items), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.itemized_tap_add), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            placeholder = { Text(stringResource(R.string.itemized_item_name, index + 1)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = if (item.amount > 0.0) String.format(Locale.getDefault(), "%.2f", item.amount) else "",
                            onValueChange = { v ->
                                val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                updateItem(item.itemId, item.copy(amount = parsed))
                            },
                            placeholder = { Text(stringResource(R.string.amount_placeholder)) },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(8.dp),
                            prefix = { Text(currencySymbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                        IconButton(onClick = { duplicateItem(item.itemId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.itemized_duplicate), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { removeItem(item.itemId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.itemized_remove), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Member assignment
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = { assignAll(item.itemId) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(stringResource(R.string.itemized_assign_all), style = MaterialTheme.typography.labelSmall)
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
                            if (item.assignedTo.size > 1) stringResource(R.string.itemized_per_person_multiple, "$currencySymbol${String.format(Locale.getDefault(), "%.2f", perPerson)}", item.assignedTo.size)
                            else stringResource(R.string.itemized_per_person_each, "$currencySymbol${String.format(Locale.getDefault(), "%.2f", perPerson)}"),
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
            Text(stringResource(R.string.itemized_add_item))
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
                            Text(stringResource(R.string.itemized_tax), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = if (taxAmt > 0.0) String.format(Locale.getDefault(), "%.2f", taxAmt) else "",
                                onValueChange = { v ->
                                    val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                    onItemizedDataChange(itemizedData.copy(taxAmount = parsed))
                                },
                                placeholder = { Text(stringResource(R.string.amount_placeholder)) },
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
                                    label = { Text(stringResource(R.string.itemized_proportional), style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = itemizedData.taxSplitMode == "equal",
                                    onClick = { onItemizedDataChange(itemizedData.copy(taxSplitMode = "equal")) },
                                    label = { Text(stringResource(R.string.itemized_equal), style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        // Tip
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.itemized_tip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = if (tipAmt > 0.0) String.format(Locale.getDefault(), "%.2f", tipAmt) else "",
                                onValueChange = { v ->
                                    val parsed = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                                    onItemizedDataChange(itemizedData.copy(tipAmount = parsed))
                                },
                                placeholder = { Text(stringResource(R.string.amount_placeholder)) },
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
                                    label = { Text(stringResource(R.string.itemized_proportional), style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = itemizedData.tipSplitMode == "equal",
                                    onClick = { onItemizedDataChange(itemizedData.copy(tipSplitMode = "equal")) },
                                    label = { Text(stringResource(R.string.itemized_equal), style = MaterialTheme.typography.labelSmall) }
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
                        Text(stringResource(R.string.itemized_subtotal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol${String.format(Locale.getDefault(), "%.2f", itemsTotal)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (taxAmt > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.itemized_tax), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol${String.format(Locale.getDefault(), "%.2f", taxAmt)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (tipAmt > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.itemized_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$currencySymbol${String.format(Locale.getDefault(), "%.2f", tipAmt)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.itemized_grand_total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("$currencySymbol${String.format(Locale.getDefault(), "%.2f", grandTotal)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                    Text(stringResource(R.string.itemized_per_member), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    activeMembers.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(member.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$currencySymbol${String.format(Locale.getDefault(), "%.2f", memberTotals[member.uid] ?: 0.0)}",
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
                stringResource(R.string.itemized_validation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!totalMatchesExpense && itemizedData.items.isNotEmpty() && isValid) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.itemized_total_mismatch_error,
                    "$currencySymbol${String.format(Locale.getDefault(), "%.2f", grandTotal)}",
                    "$currencySymbol${String.format(Locale.getDefault(), "%.2f", expenseAmount)}"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
