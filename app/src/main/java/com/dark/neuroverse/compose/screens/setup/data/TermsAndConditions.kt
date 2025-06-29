package com.dark.neuroverse.compose.screens.setup.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.compose.components.RichText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionScreen(onAgree: () -> Unit = {}) {

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        RichText(
            style = MaterialTheme.typography.bodyLarge,
            text = """
                Neuro V — Terms & Conditions & Privacy Policy
                Effective Date: 29-06-2025

                Welcome to Neuro V, an advanced offline assistant application powered by local LLM (Large Language Model) technology, leveraging llama.cpp and Guff models.

                By installing, accessing, or using Neuro V, you agree to the following Terms & Conditions and Privacy Policy.

                **1. About Neuro V**
                Neuro V is an intelligent, offline task manager and assistant application. It operates entirely offline, ensuring user data remains secure and private. The application requires certain device permissions to function effectively, detailed below.

                **2. Data Collection & Storage**
                Neuro V collects various forms of user data for offline processing and personalized performance.
                
                You can Just Ask the AI not to Collect the certain Kind of Data **( By Just Saying Don't Collect My This Type of Data ).**
                
                 To View What Kind OF Data Collection is Active, you can just Ask The AI what Data Are You Collecting Now, By Default the Ai collects All Kind of Data Like:
                
                * Textual inputs
                * User-generated notes or prompts
                * Device interaction patterns
                * Task history and preferences
                * Call logs and call history & Contact Info's
                * Installed Apps List
                * Files & Folder's Present On Device ( Excluding Audio & Images & Videos)
                * User's Chat Memory, This can Be ByPassed By Chatting in InCognito Mode
                * Accessibility interactions (only when functionality is implemented — currently not active)


                **Data Storage**
                All collected data is encrypted and stored locally on your device in a `.brain` file. This file:

                * Is secured using Android's Keystore System and hardware-backed security features where available.
                * Can only be decrypted by the user within the Neuro V application.
                * Remains entirely offline and is never transmitted to external servers.

                **Important:** Your data security is dependent on downloading Neuro V from trusted sources (e.g., the official website or verified open-source repositories). If you obtain Neuro V from unverified or third-party sources, we cannot guarantee the integrity or security of your data.

                **3. Permissions**
                To function optimally, Neuro V requests the following permissions:

                * **Accessibility Service:** To provide enhanced assistance and intelligent interaction features. *(Note: Accessibility features are not yet implemented. No accessibility-based data is currently collected.)*
                * **Default Assistant Permission:** Allows Neuro V to act as your primary assistant app for quick access.

                All permissions are required solely for offline functionality. No user data is transmitted off your device.

                **4. Data Security**
                We take your privacy and security seriously:

                * All data is encrypted at rest within the `.brain` file using advanced cryptographic standards.
                * Data decryption occurs only within the Neuro V app by the user.
                * We leverage Android’s Keystore and hardware security modules for maximum protection.
                * Neuro V operates entirely offline. No user data is transmitted to our servers or any third parties unless:

                  * You manually export data.
                  * You intentionally share content using other apps or services.

                **5. Open Source & Third-Party Risks**
                Neuro V is open-source. While this ensures transparency and user trust, downloading modified versions from untrusted sources poses significant risks to your privacy and security. Always verify sources before installation.

                We are not responsible for data breaches, security failures, or functionality issues arising from:

                * Downloading the app from unofficial sources
                * Installing modified or tampered versions

                **6. User Responsibilities**
                By using Neuro V, you agree to:

                * Install Neuro V only from trusted, verified sources
                * Keep your device secure with up-to-date system protections
                * Understand that while Neuro V prioritizes privacy, misuse or unauthorized installations can compromise security

                **7. Future Functionality**
                Planned features, including accessibility-based interactions, will be communicated transparently when implemented. Until then, no accessibility-related data is collected.

                **8. Limitation of Liability**
                Neuro V is provided "as is." We strive for high security and reliability, but:

                * We are not liable for data loss, corruption, or unauthorized access due to factors beyond our control (e.g., rooted devices, third-party tampering).
                * We are not liable for user data leaks resulting from downloading the app from unverified sources.

                **9. Contact**
                For questions or concerns, contact us at:

                * **Email:** siddheshsonar2377@gmail.com
                * **ProjectPage:** https://github.com/siddheshsonar2377/NeuroV

                By using Neuro V, you accept this agreement and acknowledge your understanding of our privacy and security practices.

                *--- End of Terms ---*
                ***--- Thank You For Downloading The App... ---***

            """.trimIndent()
        )

        Button(
            onClick = onAgree,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Agree")
        }
    }
}
