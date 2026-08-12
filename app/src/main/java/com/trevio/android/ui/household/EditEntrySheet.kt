package com.trevio.android.ui.household

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trevio.android.R
import com.trevio.android.core.designsystem.theme.*
import com.trevio.android.domain.model.Expense
import com.trevio.android.domain.model.Member
import com.trevio.android.domain.model.TransactionType
import com.trevio.android.util.HouseholdCategories
import com.trevio.android.util.HouseholdCategory
import com.trevio.android.util.AppConstants
import com.trevio.android.util.MemberStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEntrySheet(
    entry: Expense,
    members: List<Member>,
    isSaving: Boolean,
    onUpdate: (expenseId: String, amount: Double, description: String, category: String, paidBy: String, date: Long, note: String, transactionType: TransactionType) -> Unit,
    onDelete: (expenseId: String) -> Unit,
    onDismiss: () -> Unit,
    canEdit: Boolean = true,
    currencySymbol: String = "₹"
) {
    var amountText by remember { mutableStateOf(entry.amount.toString()) }
    var description by remember { mutableStateOf(entry.description) }
    var selectedCategory by remember { mutableStateOf(entry.category) }
    var selectedPaidBy by remember { mutableStateOf(entry.paidBy) }
    var note by remember { mutableStateOf(entry.note) }
    var isIncome by remember { mutableStateOf(entry.transactionType == TransactionType.INCOME) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val activeMembers = members.filter { it.status == MemberStatus.ACTIVE }
    val categories = if (isIncome) HouseholdCategories.INCOME_CATEGORIES else HouseholdCategories.EXPENSE_CATEGORIES

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.edit_entry_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_entry_close))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spent/Received toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(3.dp)
            ) {
                ToggleItem(
                    text = stringResource(R.string.edit_entry_spent),
                    selected = !isIncome,
                    onClick = {
                        isIncome = false
                        // Reset category if current is an income category
                        if (HouseholdCategories.getCategory(selectedCategory)?.isIncome == true) {
                            selectedCategory = AppConstants.DEFAULT_CATEGORY
                        }
                    },
                    modifier = Modifier.weight(1f),
                    selectedColor = MaterialTheme.colorScheme.error
                )
                ToggleItem(
                    text = stringResource(R.string.edit_entry_received),
                    selected = isIncome,
                    onClick = {
                        isIncome = true
                        // Reset category if current is an expense category
                        if (HouseholdCategories.getCategory(selectedCategory)?.isIncome != true) {
                            selectedCategory = "other_income"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Paid By / Received By — shown right below the Spent/Received
            // toggle. Label changes based on selection.
            Text(
                text = stringResource(if (isIncome) R.string.edit_entry_received_by else R.string.edit_entry_paid_by),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeMembers.forEach { member ->
                    PaidByChip(
                        name = member.displayName,
                        selected = selectedPaidBy == member.uid,
                        onClick = { selectedPaidBy = member.uid }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    val parts = filtered.split(".")
                    amountText = if (parts.size <= 2) filtered else parts[0] + "." + parts.drop(1).joinToString("")
                },
                label = { Text(stringResource(R.string.edit_entry_amount)) },
                prefix = { Text(currencySymbol) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                label = { Text(stringResource(R.string.edit_entry_description)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category
            Text(
                text = stringResource(R.string.edit_entry_category),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    CategoryChip(
                        category = cat,
                        selected = selectedCategory == cat.key,
                        onClick = { selectedCategory = cat.key }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 500) note = it },
                label = { Text(stringResource(R.string.edit_entry_note)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            if (!canEdit) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.edit_entry_view_only),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showDeleteConfirm) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.common_cancel)) }
                        Button(
                            onClick = { onDelete(entry.expenseId) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(R.string.edit_entry_confirm_delete)) }
                    } else {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.edit_entry_delete), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.edit_entry_delete))
                        }
                        Button(
                            onClick = {
                                val amount = amountText.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    val transactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
                                    onUpdate(
                                        entry.expenseId,
                                        amount,
                                        description,
                                        selectedCategory,
                                        selectedPaidBy,
                                        entry.date,
                                        note,
                                        transactionType
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSaving && (amountText.toDoubleOrNull() ?: 0.0) > 0
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(stringResource(R.string.edit_entry_save_changes))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryChip(
    category: HouseholdCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) category.color.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .border(
                1.dp,
                if (selected) category.color else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = stringResource(category.labelResId),
            tint = category.color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(category.labelResId),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) category.color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PaidByChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
