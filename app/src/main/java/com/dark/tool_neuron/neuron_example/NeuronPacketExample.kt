package com.dark.tool_neuron.neuron_example

import com.dark.tool_neuron.engine.EmbeddingConfig
import com.dark.tool_neuron.engine.EmbeddingEngine
import com.neuronpacket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class NeuronPacketExample(private val cacheDir: File) {
    private val packetManager = NeuronPacketManager()
    private val embeddingEngine = EmbeddingEngine()

    suspend fun initializeEmbedding(modelPath: String) = withContext(Dispatchers.IO) {
        val config = EmbeddingConfig(modelPath = modelPath)
        embeddingEngine.initialize(config)
    }

    suspend fun generateEmbedding(text: String): FloatArray? {
        return embeddingEngine.embed(text)
    }

    suspend fun createKnowledgePacket(
        outputPath: String,
        name: String,
        documents: List<DocumentChunk>,
        adminPassword: String,
        readOnlyUsers: List<UserCredentials> = emptyList()
    ): Result<ExportResult> {
        val payload = serializeDocuments(documents)

        val config = ExportConfig(
            adminPassword = adminPassword,
            readOnlyUsers = readOnlyUsers,
            loadingMode = LoadingMode.EMBEDDED,
            compress = true
        )

        val metadata = PacketMetadata(
            name = name,
            domain = "knowledge",
            loadingMode = LoadingMode.EMBEDDED
        )

        return packetManager.export(File(outputPath), metadata, payload, config)
    }

    suspend fun loadKnowledgePacket(
        packetPath: String,
        password: String
    ): Result<List<DocumentChunk>> {
        val openResult = packetManager.open(File(packetPath))
        if (openResult.isFailure) {
            return Result.failure(openResult.exceptionOrNull()!!)
        }

        val authResult = packetManager.authenticate(password)
        if (authResult.isFailure) {
            return Result.failure(authResult.exceptionOrNull()!!)
        }

        val session = authResult.getOrThrow()
        val payloadResult = packetManager.decryptPayload(session)
        if (payloadResult.isFailure) {
            return Result.failure(payloadResult.exceptionOrNull()!!)
        }

        val documents = deserializeDocuments(payloadResult.getOrThrow())
        return Result.success(documents)
    }

    suspend fun searchSimilar(
        documents: List<DocumentChunk>,
        query: String,
        topK: Int = 5,
        threshold: Float = 0.5f
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val queryEmbedding = generateEmbedding(query) ?: return@withContext emptyList()

        documents
            .filter { it.embedding != null }
            .map { doc ->
                val score = cosineSimilarity(queryEmbedding, doc.embedding!!)
                SearchResult(doc, score)
            }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(topK)
    }

    suspend fun closePacket() {
        packetManager.close()
    }

    fun getPacketInfo() = packetManager.getPacketInfo()

    private fun serializeDocuments(documents: List<DocumentChunk>): ByteArray {
        val bos = ByteArrayOutputStream()
        val dos = DataOutputStream(bos)

        dos.writeInt(documents.size)
        for (doc in documents) {
            dos.writeUTF(doc.id)
            dos.writeUTF(doc.content)
            dos.writeUTF(doc.source)

            val hasEmbedding = doc.embedding != null
            dos.writeBoolean(hasEmbedding)
            if (hasEmbedding) {
                dos.writeInt(doc.embedding!!.size)
                for (f in doc.embedding!!) {
                    dos.writeFloat(f)
                }
            }
        }

        return bos.toByteArray()
    }

    private fun deserializeDocuments(data: ByteArray): List<DocumentChunk> {
        val bis = ByteArrayInputStream(data)
        val dis = DataInputStream(bis)

        val count = dis.readInt()
        val documents = mutableListOf<DocumentChunk>()

        repeat(count) {
            val id = dis.readUTF()
            val content = dis.readUTF()
            val source = dis.readUTF()

            val hasEmbedding = dis.readBoolean()
            val embedding = if (hasEmbedding) {
                val size = dis.readInt()
                FloatArray(size) { dis.readFloat() }
            } else null

            documents.add(DocumentChunk(id, content, source, embedding))
        }

        return documents
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }
}

data class DocumentChunk(
    val id: String,
    val content: String,
    val source: String,
    var embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentChunk
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}

data class SearchResult(
    val document: DocumentChunk,
    val score: Float
)