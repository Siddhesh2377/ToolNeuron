package com.dark.tool_neuron.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun TermsAndConditionsScreen(
    onAccept: () -> Unit
) {
    val scrollState = rememberScrollState()
    var isScrolledToBottom by remember { mutableStateOf(false) }

    // Check if user has scrolled to bottom
    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        isScrolledToBottom = scrollState.value >= scrollState.maxValue - 100 || scrollState.maxValue == 0
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = rDp(20.dp))
        ) {
            // Header
            Spacer(modifier = Modifier.height(rDp(40.dp)))

            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = rDp(8.dp))
            )

            Text(
                text = "Please read carefully before using ToolNeuron",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = rDp(24.dp))
            )

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = rDp(16.dp))
            ) {
                TermsSection(
                    title = "1. Acceptance of Terms",
                    content = """
                        By downloading, installing, or using ToolNeuron ("the App"), you agree to be bound by these Terms and Conditions. If you do not agree to these terms, you must immediately uninstall the App and discontinue its use.
                    """.trimIndent()
                )

                TermsSection(
                    title = "2. Nature of Service",
                    content = """
                        ToolNeuron is a fully offline AI assistant that runs AI models locally on your Android device. The App enables you to:
                        • Run Large Language Models (LLMs) in GGUF format
                        • Generate images using Stable Diffusion 1.5
                        • Download models from Hugging Face (one-time download only)
                        • Load custom GGUF and SD 1.5 models from local storage
                        
                        ALL AI PROCESSING OCCURS ENTIRELY OFFLINE. Your data is stored securely on-device using MemoryVault encrypted storage and NEVER leaves your device except when downloading models from Hugging Face.
                    """.trimIndent()
                )

                TermsSection(
                    title = "3. User Responsibility for Generated Content",
                    content = """
                        YOU ARE SOLELY RESPONSIBLE FOR ALL CONTENT GENERATED THROUGH THE APP.
                        
                        This includes but is not limited to:
                        • Text and images created using AI models
                        • Content that may be harmful, illegal, explicit, defamatory, or violates any laws
                        • NSFW (Not Safe For Work) content including adult or sexually explicit material
                        • Content that infringes on intellectual property rights
                        • Content that violates the rights or safety of any person or entity
                        
                        The developer and ToolNeuron team bear NO LIABILITY for content you generate, regardless of how the content was created, what prompts were used, or which models were loaded.
                    """.trimIndent()
                )

                TermsSection(
                    title = "4. Third-Party AI Models",
                    content = """
                        ToolNeuron allows you to download and use AI models from Hugging Face, as well as load custom models from your device storage.
                        
                        IMPORTANT DISCLAIMERS:
                        • The developer did NOT create, train, or tune these models
                        • The developer has NO CONTROL over model behavior, biases, or outputs
                        • Models may produce unexpected, inaccurate, biased, or harmful content
                        • The developer provides NO WARRANTY regarding model performance, safety, or legality
                        
                        The default model list consists of publicly available models from Hugging Face. However, the developer is NOT RESPONSIBLE for any content these models generate.
                        
                        If you choose to load custom-tuned, fine-tuned, or modified models from local storage, you assume FULL RESPONSIBILITY for understanding the model's behavior and any content it produces.
                    """.trimIndent()
                )

                TermsSection(
                    title = "5. Model Downloads",
                    content = """
                        The ONLY network connection made by ToolNeuron is to download AI models from Hugging Face. This occurs when:
                        • You select a model from the in-app model list
                        • The model is not already present on your device
                        
                        MODEL DOWNLOAD RESPONSIBILITY:
                        • You are responsible for ensuring you have permission to download and use models
                        • Models are subject to their respective licenses on Hugging Face
                        • Network and storage costs are your responsibility
                        • Downloaded models are stored locally and never uploaded anywhere
                        
                        NO OTHER DATA IS TRANSMITTED. Your prompts, conversations, generated content, and all other user data remain entirely on your device.
                    """.trimIndent()
                )

                TermsSection(
                    title = "6. Prohibited Uses",
                    content = """
                        You agree NOT to use ToolNeuron to:
                        • Generate content that violates local, national, or international laws
                        • Create content for illegal activities including but not limited to fraud, harassment, or harm
                        • Produce child sexual abuse material (CSAM) or content exploiting minors
                        • Generate content that promotes terrorism, violence, or extremism
                        • Create deepfakes or impersonate others without consent
                        • Violate copyright, trademark, or other intellectual property rights
                        
                        Violation of these prohibitions may result in legal action. You acknowledge that such violations are YOUR SOLE RESPONSIBILITY.
                    """.trimIndent()
                )

                TermsSection(
                    title = "7. Privacy and Data Security",
                    content = """
                        ToolNeuron is designed with privacy as a core principle:
                        
                        COMPLETE OFFLINE OPERATION:
                        • All AI inference occurs locally on your device
                        • Your prompts, conversations, and generated content are NEVER transmitted anywhere
                        • We do NOT log, collect, or analyze your usage data
                        • No telemetry, analytics, or tracking of any kind
                        
                        ENCRYPTED LOCAL STORAGE:
                        • All data is stored using MemoryVault with AES-256-GCM encryption
                        • Data is hardware-backed via Android KeyStore
                        • Storage uses LZ4 compression for efficiency
                        
                        NETWORK USAGE:
                        • The ONLY network activity is downloading models from Hugging Face
                        • No user data is ever uploaded or transmitted
                        
                        YOUR RESPONSIBILITY:
                        • You are responsible for securing your device and backups
                        • We are NOT RESPONSIBLE for data loss, corruption, or unauthorized access to your device
                        • If your device is compromised, your locally stored data may be at risk
                    """.trimIndent()
                )

                TermsSection(
                    title = "8. No Warranty",
                    content = """
                        THE APP IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO:
                        • Warranties of merchantability or fitness for a particular purpose
                        • Warranties regarding accuracy, reliability, or availability
                        • Warranties that the App will be error-free or uninterrupted
                        
                        AI-GENERATED CONTENT DISCLAIMER:
                        • AI models may produce inaccurate, biased, offensive, or harmful outputs
                        • Content accuracy is NOT GUARANTEED
                        • Medical, legal, financial, or other professional advice generated by AI should NOT be relied upon
                        • You should verify all AI-generated information independently
                        
                        DEVICE COMPATIBILITY:
                        • The App requires significant device resources (RAM, storage, processing power)
                        • Performance depends entirely on your device hardware
                        • The developer provides NO WARRANTY that the App will run on your specific device
                    """.trimIndent()
                )

                TermsSection(
                    title = "9. Limitation of Liability",
                    content = """
                        TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE DEVELOPER AND TOOLNEURON TEAM SHALL NOT BE LIABLE FOR:
                        • Any damages arising from your use or inability to use the App
                        • Content you generate or actions you take based on AI outputs
                        • Data loss, corruption, or security breaches on your device
                        • Harm caused to you or third parties from AI-generated content
                        • Legal consequences resulting from content you create or distribute
                        • Performance issues related to device hardware limitations
                        • Storage space consumption or battery drain
                        • Issues arising from model downloads or model licensing
                        
                        This limitation applies regardless of whether the developer was advised of the possibility of such damages.
                    """.trimIndent()
                )

                TermsSection(
                    title = "10. Indemnification",
                    content = """
                        You agree to INDEMNIFY, DEFEND, and HOLD HARMLESS the developer, ToolNeuron team, and their affiliates from any claims, damages, losses, liabilities, and expenses (including legal fees) arising from:
                        • Your use of the App
                        • Content you generate using the App
                        • Your violation of these Terms and Conditions
                        • Your violation of any laws or rights of third parties
                        • Models you download or load from local storage
                        • Any breach of model licenses or terms of use
                    """.trimIndent()
                )

                TermsSection(
                    title = "11. Age Requirement",
                    content = """
                        You must be at least 18 years old to use ToolNeuron. If you are under 18, you may only use the App with the supervision and consent of a parent or legal guardian.
                        
                        The App may generate adult content depending on the models loaded and prompts used. By using the App, you confirm you are of legal age in your jurisdiction to access such content.
                    """.trimIndent()
                )

                TermsSection(
                    title = "12. Open Source and Modifications",
                    content = """
                        ToolNeuron is open-source software. You may view, modify, and distribute the source code subject to the license terms.
                        
                        MODIFIED VERSIONS:
                        • If you modify the App, you are responsible for all changes and resulting behavior
                        • The original developer is NOT LIABLE for issues arising from modifications
                        • Modified versions must comply with the open-source license
                    """.trimIndent()
                )

                TermsSection(
                    title = "13. Updates and Changes",
                    content = """
                        The developer reserves the right to:
                        • Modify these Terms and Conditions at any time
                        • Update, modify, or discontinue the App or any features
                        • Remove or add models to the default model list
                        
                        Continued use of the App after changes constitutes acceptance of the updated terms.
                    """.trimIndent()
                )

                TermsSection(
                    title = "14. System Requirements",
                    content = """
                        MINIMUM REQUIREMENTS:
                        • Android API 26 (Oreo) or higher
                        • Minimum 6GB device RAM recommended
                        • Sufficient storage space for models (models range from 500MB to 10GB+)
                        
                        PERFORMANCE NOTES:
                        • AI inference is computationally intensive and may drain battery
                        • Generation speed depends entirely on device hardware
                        • Older or lower-end devices may experience slow performance or crashes
                    """.trimIndent()
                )

                TermsSection(
                    title = "15. Jurisdiction and Governing Law",
                    content = """
                        These Terms and Conditions are governed by the laws of India. Any disputes arising from your use of the App shall be subject to the exclusive jurisdiction of courts in Mumbai, Maharashtra, India.
                    """.trimIndent()
                )

                TermsSection(
                    title = "16. Severability",
                    content = """
                        If any provision of these Terms and Conditions is found to be invalid or unenforceable, the remaining provisions shall continue in full force and effect.
                    """.trimIndent()
                )

                TermsSection(
                    title = "17. Contact Information",
                    content = """
                        For questions about these Terms and Conditions, please contact:
                        
                        Email: siddheshsonar2377@gmail.com
                        GitHub: https://github.com/Siddhesh2377/ToolNeuron
                        Discord: https://discord.gg/mVPwHDhrAP
                        
                        Last Updated: January 2026
                    """.trimIndent()
                )

                // Final acknowledgment
                Spacer(modifier = Modifier.height(rDp(24.dp)))

                Text(
                    text = "BY CLICKING 'I ACCEPT' BELOW, YOU ACKNOWLEDGE THAT YOU HAVE READ, UNDERSTOOD, AND AGREE TO BE BOUND BY THESE TERMS AND CONDITIONS. YOU ACCEPT FULL RESPONSIBILITY FOR ALL CONTENT GENERATED USING THIS APP.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = rDp(16.dp))
                )

                Spacer(modifier = Modifier.height(rDp(16.dp)))
            }

            // Accept button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = rDp(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                ActionTextButton(
                    onClickListener = {
                        if (isScrolledToBottom) {
                            onAccept()
                        }
                    },
                    icon = Icons.Default.Check,
                    text = if (isScrolledToBottom) "I Accept" else "Scroll to Continue",
                    contentDescription = "Accept Terms",
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScrolledToBottom)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isScrolledToBottom)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun TermsSection(
    title: String,
    content: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = rDp(20.dp))
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = rDp(8.dp))
        )

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}