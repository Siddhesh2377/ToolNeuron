package com.dark.neuroverse.userdata.ntds.temp

// List of Subjects
val subjects = listOf(
    "I",
    "You",
    "He",
    "She",
    "It",
    "We",
    "They",
    "The cat",
    "The teacher",
    "My friend"
)

// List of Verbs (Actions)
val verbs = listOf(
    "eat",
    "play",
    "run",
    "watch",
    "read",
    "like",
    "hate",
    "make",
    "see",
    "learn"
)

// List of Objects (Nouns to receive action)
val objects = listOf(
    "apples",
    "games",
    "books",
    "movies",
    "music",
    "pizza",
    "stories",
    "homework"
)

// List of Adjectives (Describing words)
val adjectives = listOf(
    "interesting",
    "delicious",
    "boring",
    "fun",
    "new",
    "difficult",
    "exciting"
)

// List of Greetings/Interjections
val interjections = listOf(
    "Hi",
    "Hello",
    "Hey",
    "Wow",
    "Oops"
)

// Different sentence patterns as Kotlin functions or templates
val sentenceStructures = listOf(
    "{subject} {verb}.", // Simple: I eat.
    "{subject} {verb} {object}.", // With object: She reads books.
    "{subject} {verb} {adjective} {object}.", // With adjective: They watch exciting movies.
    "{interjection}!", // Just greeting: Hi!
    "{interjection}! {subject} {verb} {object}." // Greeting with action: Hello! I read books.
)


fun main() {
    val userInput = "Hello"  // Example input

    val response = parseUserInput(userInput)

    println("User said: $userInput")
    println("Micro AI Response: $response")
}


fun parseUserInput(input: String): String {
    val lowerInput = input.lowercase()

    val detectedSubject = subjects.find { lowerInput.contains(it.lowercase()) } ?: subjects.random()
    val detectedVerb = verbs.find { lowerInput.contains(it.lowercase()) } ?: verbs.random()
    val detectedObject = objects.find { lowerInput.contains(it.lowercase()) } ?: objects.random()
    val detectedAdj = adjectives.find { lowerInput.contains(it.lowercase()) } ?: adjectives.random()
    val detectedInterjection =
        interjections.find { lowerInput.contains(it.lowercase()) } ?: interjections.random()

    val structure = sentenceStructures.random()

    return structure
        .replace("{subject}", detectedSubject)
        .replace("{verb}", detectedVerb)
        .replace("{object}", detectedObject)
        .replace("{adjective}", detectedAdj)
        .replace("{interjection}", detectedInterjection)
}


fun generateSentence(): String {
    val subject = subjects.random()
    val verb = verbs.random()
    val obj = objects.random()
    val adj = adjectives.random()
    val interjection = interjections.random()
    val structure = sentenceStructures.random()

    return structure
        .replace("{subject}", subject)
        .replace("{verb}", verb)
        .replace("{object}", obj)
        .replace("{adjective}", adj)
        .replace("{interjection}", interjection)
}
