package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AiSupportTab(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val aiAnalysisState by viewModel.aiAnalysisState.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val authUserEmail by viewModel.authUserEmail.collectAsState()
    
    var selectedSubTab by remember { mutableStateOf("analysis") } // "analysis" or "chat"
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PolishBg)
            .padding(16.dp)
    ) {
        // High polish AI header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PolishSecondary)
                    .border(BorderStroke(1.5.dp, PolishPrimary), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "AI Head",
                    tint = PolishPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Guardian AI Support",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = PolishTextDark
                )
                Text(
                    text = "Smart financial advisor powered by Gemini 3.5",
                    fontSize = 12.sp,
                    color = PolishTextSlate
                )
            }
        }

        // Sub-tabs controller
        TabRow(
            selectedTabIndex = if (selectedSubTab == "analysis") 0 else 1,
            containerColor = PolishTertiary,
            contentColor = PolishPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, PolishBorder), RoundedCornerShape(12.dp))
                .height(48.dp)
        ) {
            Tab(
                selected = selectedSubTab == "analysis",
                onClick = { selectedSubTab = "analysis" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Analytics, contentDescription = "Summary Analysis", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Financial Health", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedSubTab == "chat",
                onClick = { selectedSubTab = "chat" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Chat, contentDescription = "Chat Support", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chat Guardian", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content display with animated transitions
        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "AiSubTabTransition",
            modifier = Modifier.weight(1f)
        ) { subTab ->
            when (subTab) {
                "analysis" -> FinancialHealthAdvisor(
                    aiAnalysisState = aiAnalysisState,
                    onTriggerAnalysis = { viewModel.generateAnalysis() }
                )
                "chat" -> ChatGuardianSupport(
                    chatHistory = chatHistory,
                    isChatLoading = isChatLoading,
                    userEmail = authUserEmail ?: "",
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    onClearHistory = { viewModel.clearChatHistory() }
                )
            }
        }
    }
}

@Composable
fun FinancialHealthAdvisor(
    aiAnalysisState: AiAnalysisState,
    onTriggerAnalysis: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("financial_health_view")
    ) {
        when (aiAnalysisState) {
            is AiAnalysisState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = "Shield",
                        tint = PolishTextSlate,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Secure Personal Financial Audit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = PolishTextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Study your transactions, budgets, auto-debited loans, and ongoing subscriptions. Our sandboxed client-side AI provides comprehensive reviews isolated completely from other user tables.",
                        fontSize = 13.sp,
                        color = PolishTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onTriggerAnalysis,
                        colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("trigger_audit_button")
                    ) {
                        Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = "Compute")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Expenses & Budgets", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            is AiAnalysisState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = PolishPrimary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Gemini is auditing active ledger tables...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = PolishTextDark
                    )
                    Text(
                        text = "Compiling financial context strictly for your account",
                        fontSize = 11.sp,
                        color = PolishTextSlate
                    )
                }
            }
            is AiAnalysisState.Success -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Financial Intelligence Report",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PolishTextDark
                        )
                        IconButton(
                            onClick = onTriggerAnalysis,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Re-analyze",
                                tint = PolishPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Quick stats indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PolishTertiary),
                            border = BorderStroke(1.dp, PolishBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Budget Efficiency", fontSize = 10.sp, color = PolishTextSlate)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("89% Score", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PolishPrimary)
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PolishTertiary),
                            border = BorderStroke(1.dp, PolishBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Active Support Status", fontSize = 10.sp, color = PolishTextSlate)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Excellent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PolishPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PolishSurface),
                        border = BorderStroke(1.dp, PolishBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            item {
                                Text(
                                    text = aiAnalysisState.analysis,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = PolishTextDark
                                )
                            }
                        }
                    }
                }
            }
            is AiAnalysisState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Failure",
                        tint = PolishAlertRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Failed to Generate Intelligence Audit",
                        fontWeight = FontWeight.Bold,
                        color = PolishTextDark,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = aiAnalysisState.message,
                        fontSize = 12.sp,
                        color = PolishAlertRed,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onTriggerAnalysis,
                        colors = ButtonDefaults.buttonColors(containerColor = PolishPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Retry Audit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatDataFrameSecureNotice(userEmail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PolishTertiary),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PolishBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = "Shield Guard",
                tint = PolishPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Isolated Client Context: This sandbox chat studies only ${userEmail.ifEmpty { "your" }}'s ledger sheets.",
                fontSize = 11.sp,
                color = PolishTextDark,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ChatGuardianSupport(
    chatHistory: List<ChatMessage>,
    isChatLoading: Boolean,
    userEmail: String,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // Scroll to bottom when new messages arrive:
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            scrollState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    val suggestionPrompts = listOf(
        "Analyze category spending",
        "Am I exceeding budgets?",
        "How is auto-debits drain ratio?",
        "Actionable tips to save $150"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_guardian_view")
    ) {
        // Secure context notice
        ChatDataFrameSecureNotice(userEmail)

        // Message board
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PolishSurface)
                .border(BorderStroke(1.dp, PolishBorder), RoundedCornerShape(12.dp))
        ) {
            if (chatHistory.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = "AI Idle",
                        tint = PolishTextSlate,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "I'm ready under User Environment Isolation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PolishTextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ask me anything about your current budget limit balances, credit-card/cash outflows, bank connections, loans or subscriptions.",
                        fontSize = 12.sp,
                        color = PolishTextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Suggestion prompts
                    Text(
                        text = "Quick Support Starters:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PolishTextSlate,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestionPrompts.forEach { prompt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PolishTertiary),
                                border = BorderStroke(0.5.dp, PolishBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSendMessage(prompt) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = "Spark",
                                        tint = PolishPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = prompt, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PolishTextDark)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sandbox History (Active User Only)",
                            fontSize = 11.sp,
                            color = PolishTextSlate,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Clear All",
                            fontSize = 11.sp,
                            color = PolishAlertRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onClearHistory() }
                                .padding(4.dp)
                        )
                    }
                    Divider(color = PolishBorder)
                    LazyColumn(
                        state = scrollState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(chatHistory) { msg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (msg.isUser) PolishSecondary else PolishTertiary
                                    ),
                                    border = BorderStroke(1.dp, PolishBorder),
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (msg.isUser) 12.dp else 0.dp,
                                        bottomEnd = if (msg.isUser) 0.dp else 12.dp
                                    ),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = msg.text,
                                            fontSize = 13.sp,
                                            color = PolishTextDark,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (isChatLoading) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PolishTertiary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.widthIn(max = 180.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = PolishPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Analyzing ledger...",
                                                fontSize = 12.sp,
                                                color = PolishTextSlate
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input frame
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = rawInputText,
                onValueChange = { rawInputText = it },
                placeholder = { Text("Ask about your finances...", fontSize = 13.sp, color = PolishTextSlate) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (rawInputText.isNotBlank() && !isChatLoading) {
                            onSendMessage(rawInputText)
                            rawInputText = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PolishPrimary,
                    unfocusedBorderColor = PolishBorder,
                    focusedContainerColor = PolishSurface,
                    unfocusedContainerColor = PolishSurface,
                    focusedTextColor = PolishTextDark,
                    unfocusedTextColor = PolishTextDark
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field")
            )

            FloatingActionButton(
                onClick = {
                    if (rawInputText.isNotBlank() && !isChatLoading) {
                        onSendMessage(rawInputText)
                        rawInputText = ""
                        keyboardController?.hide()
                    }
                },
                containerColor = PolishPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send message",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
