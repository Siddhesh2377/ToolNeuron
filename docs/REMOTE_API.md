# Remote API Access Documentation

ToolNeuron Remote API v2.1.0+ - OpenAI-Compatible API for Local Network Access

## Overview

New in version 2.1.0, ToolNeuron can now be used as an AI server on your local network. The API is fully OpenAI-compatible, allowing you to use it with existing tools and libraries designed for OpenAI's API.

## Key Features

- **OpenAI Compatibility**: Supports standard `/v1/chat/completions` and `/v1/images/generations` endpoints
- **Streaming Support**: Real-time token streaming via Server-Sent Events (SSE)
- **Network Discovery**: Broadcasts via Android NSD (Network Service Discovery) as `ToolNeuron-API.local`
- **Configurable Port**: Default port is 11434 (compatible with Ollama tools)
- **Security**: Can be enabled or disabled via the Settings screen
- **Multi-turn Conversations**: Full conversation history support with proper role-based messages
- **Adjustable Parameters**: Control temperature, max tokens, top-p sampling, and more

## Feature Set

| Endpoint | Method | Description |
|----------|--------|-------------|
| `GET /` | GET | Health check (basic) |
| `GET /health` | GET | Health check (OpenAI-compatible) |
| `GET /healthz` | GET | Kubernetes-style health check |
| `GET /v1` | GET | API info endpoint |
| `GET /api/v1` | GET | API info endpoint (alternative) |
| `GET /models` | GET | List text models (alternative) |
| `GET /v1/models` | GET | List available GGUF text models |
| `GET /v1/models/{model_id}` | GET | Get specific model details |
| `GET /v1/image-models` | GET | List available Stable Diffusion image models |
| `POST /v1/chat/completions` | POST | Text generation (streaming or non-streaming) |
| `POST /v1/images/generations` | POST | Generate images with optional model selection |
| `POST /v1/embeddings` | POST | Text embeddings (not yet implemented) |
| `OPTIONS /v1/chat/completions` | OPTIONS | CORS/OPTIONS support |
| `OPTIONS /v1/images/generations` | OPTIONS | CORS/OPTIONS support |
| `OPTIONS /v1/models` | OPTIONS | CORS/OPTIONS support |

---

## OpenWebUI Integration

ToolNeuron is fully compatible with **OpenWebUI**, a powerful web interface for LLMs.

### Setup in OpenWebUI

1. **Open OpenWebUI**
   - Go to Settings → Admin Settings → Models
   - Or Settings → Connections → New Connection

2. **Configure Connection**
   - **API URL**: `http://<device-ip>:11434/v1`
   - **API Key**: Leave empty or use any value (not required)
   - **Connection Name**: ToolNeuron

3. **Models will automatically appear**
   - All GGUF models loaded in ToolNeuron
   - Ready to use in chat interface

### Common OpenWebUI Features Supported

| Feature | Status | Notes |
|---------|--------|-------|
| Chat Completions | ✅ | Streaming and non-streaming |
| Model List | ✅ | Auto-discovered |
| Health Checks | ✅ | Multiple standards |
| Image Generation | ✅ | With model selection |
| Temperature | ✅ | Inference parameter control |
| Max Tokens | ✅ | Response length limiting |
| System Prompt | ✅ | Via system role message |

### Troubleshooting Connection Issues

| Issue | Solution |
|-------|----------|
| "Connection failed" | Check device IP, ensure Remote API is enabled, check firewall |
| "Cannot connect to endpoint" | Use `/v1` path when configuring (e.g., `http://192.168.1.100:11434/v1`) |
| "No models appearing" | Ensure models are loaded in ToolNeuron, check `/v1/models` endpoint |
| "Slow responses" | Normal on first request (model loading), increase timeout in OpenWebUI settings |

---

## API Examples

### 1. Basic Health Check

Retrieve all GGUF models currently loaded in ToolNeuron.

```bash
curl http://<device-ip>:11434/v1/models
```

**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "id": "llama-2-7b",
      "object": "model",
      "created": 1699564800,
      "owned_by": "toolneuron-local"
    },
    {
      "id": "mistral-7b",
      "object": "model",
      "created": 1699564800,
      "owned_by": "toolneuron-local"
    }
  ]
}
```

---

### 3. List Available Image Models

Retrieve all Stable Diffusion models currently loaded in ToolNeuron.

```bash
curl http://<device-ip>:11434/v1/image-models
```

**Response:**
```json
{
  "object": "list",
  "data": [
    {
      "id": "stable-diffusion-1.5",
      "object": "model",
      "created": 1699564800,
      "owned_by": "toolneuron-local"
    },
    {
      "id": "stable-diffusion-xl",
      "object": "model",
      "created": 1699564800,
      "owned_by": "toolneuron-local"
    }
  ]
}
```

---

### 4. Chat Completions (Non-streaming)

Generate text with the model returning the full response at once.

```bash
curl http://<device-ip>:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-2-7b",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Explain quantum computing in simple terms."}
    ],
    "max_tokens": 256,
    "temperature": 0.7
  }'
```

**Response:**
```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1699564800,
  "model": "llama-2-7b",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Quantum computing uses quantum bits (qubits) instead of regular bits..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 42,
    "completion_tokens": 128,
    "total_tokens": 170
  }
}
```

---

### 4. Chat Completions (Streaming)

Stream tokens in real-time using Server-Sent Events (SSE). Ideal for responsive UX.

```bash
curl http://<device-ip>:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-2-7b",
    "messages": [{"role": "user", "content": "Hello AI!"}],
    "stream": true,
    "max_tokens": 512
  }'
```

**Response (Server-Sent Events):**
```
data: {"id":"chatcmpl-123","object":"chat.completion","created":1699564800,"model":"toolneuron-local","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"}}]}

data: {"id":"chatcmpl-123","object":"chat.completion","created":1699564800,"model":"toolneuron-local","choices":[{"index":0,"delta":{"role":"assistant","content":" there"}}]}

data: {"id":"chatcmpl-123","object":"chat.completion","created":1699564800,"model":"toolneuron-local","choices":[{"index":0,"delta":{"role":"assistant","content":"!"}}]}

data: [DONE]
```

---

### 6. Image Generation

Generate images using Stable Diffusion with optional model selection.

```bash
curl http://<device-ip>:11434/v1/images/generations \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "A serene mountain landscape at sunset",
    "model": "stable-diffusion-1.5",
    "size": "512x512",
    "n": 1,
    "response_format": "b64_json"
  }'
```

**Response:**
```json
{
  "created": 1699564800,
  "data": [
    {
      "b64_json": "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIACAIAAAB7QOjdAAAA..."
    }
  ]
}
```

**Notes:**
- If `model` is not specified, the currently loaded diffusion model is used
- Use `/v1/image-models` to get the list of available models
- Model loading may take a few seconds on the first request

---

### 7. Multi-turn Conversation

Maintain conversation context with system prompts and history.

```bash
curl http://<device-ip>:11434/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "llama-2-7b",
    "messages": [
      {"role": "system", "content": "You are a Python programming expert."},
      {"role": "user", "content": "How do I read a file in Python?"},
      {"role": "assistant", "content": "You can use the open() function..."},
      {"role": "user", "content": "Can you show me an example?"}
    ],
    "temperature": 0.5,
    "max_tokens": 256
  }'
```

---

## Request Parameters

### Chat Completions Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `model` | string | Yes | - | Model ID to use (get list from `/v1/models`) |
| `messages` | array | Yes | - | Array of message objects with `role` and `content` |
| `stream` | boolean | No | false | Enable streaming responses |
| `temperature` | float | No | 0.7 | Sampling temperature (0.0 - 2.0) |
| `max_tokens` | integer | No | 512 | Maximum tokens to generate |
| `top_p` | float | No | 1.0 | Nucleus sampling parameter (0.0 - 1.0) |
| `frequency_penalty` | float | No | 0.0 | Penalize frequent tokens (-2.0 - 2.0) |
| `presence_penalty` | float | No | 0.0 | Penalize repeated tokens (-2.0 - 2.0) |
| `stop` | array | No | - | Sequences where generation stops |

**Message Object Format:**
```json
{
  "role": "system|user|assistant",
  "content": "Message text"
}
```

Valid roles:
- `system`: System instructions/prompt
- `user`: User message
- `assistant`: Assistant response (for history)

### Image Generation Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `prompt` | string | Yes | - | Description of the image to generate |
| `model` | string | No | - | Image model to use (get list from `/v1/image-models`); uses currently loaded model if not specified |
| `size` | string | No | "512x512" | Image size (e.g., "512x512", "768x768") |
| `n` | integer | No | 1 | Number of images to generate (always 1) |
| `response_format` | string | No | "b64_json" | Response format ("b64_json" or "url") |
| `user` | string | No | - | Optional user identifier |

---

## Integration Examples

### Python with `requests`

Simple HTTP library integration.

```python
import requests
import json

response = requests.post(
    "http://<device-ip>:11434/v1/chat/completions",
    json={
        "model": "llama-2-7b",
        "messages": [{"role": "user", "content": "Hello!"}],
        "stream": False
    }
)
result = response.json()
print(result["choices"][0]["message"]["content"])
```

### Python with `openai` Library (Compatible)

Use the official OpenAI SDK with ToolNeuron as backend.

```python
from openai import OpenAI

client = OpenAI(
    api_key="not-needed",
    base_url="http://<device-ip>:11434/v1"
)

response = client.chat.completions.create(
    model="llama-2-7b",
    messages=[{"role": "user", "content": "Explain AI"}],
    temperature=0.7,
    max_tokens=256
)
print(response.choices[0].message.content)
```

### Python with Streaming

Stream responses for real-time output.

```python
from openai import OpenAI

client = OpenAI(
    api_key="not-needed",
    base_url="http://<device-ip>:11434/v1"
)

stream = client.chat.completions.create(
    model="llama-2-7b",
    messages=[{"role": "user", "content": "Write a poem"}],
    stream=True
)

for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="", flush=True)
```

### Node.js with `axios`

JavaScript integration example.

```javascript
const axios = require('axios');

const response = await axios.post(
  'http://<device-ip>:11434/v1/chat/completions',
  {
    model: 'llama-2-7b',
    messages: [{ role: 'user', content: 'Hello AI!' }],
    stream: false,
    max_tokens: 256
  }
);
console.log(response.data.choices[0].message.content);
```

### Node.js with Streaming

```javascript
const axios = require('axios');

const response = await axios.post(
  'http://<device-ip>:11434/v1/chat/completions',
  {
    model: 'llama-2-7b',
    messages: [{ role: 'user', content: 'Hello AI!' }],
    stream: true,
    max_tokens: 256
  },
  { responseType: 'stream' }
);

response.data.on('data', (chunk) => {
  const lines = chunk.toString().split('\n');
  lines.forEach(line => {
    if (line.startsWith('data: ') && line !== 'data: [DONE]') {
      const data = JSON.parse(line.substring(6));
      if (data.choices?.[0]?.delta?.content) {
        process.stdout.write(data.choices[0].delta.content);
      }
    }
  });
});
```

### LangChain Integration

Use ToolNeuron with LangChain for advanced AI workflows.

```python
from langchain.chat_models import ChatOpenAI

llm = ChatOpenAI(
    model_name="llama-2-7b",
    openai_api_base="http://<device-ip>:11434/v1",
    openai_api_key="not-needed"
)

response = llm.invoke("Explain machine learning")
print(response.content)
```

### AutoGPT Integration

```python
from langchain.chat_models import ChatOpenAI
from langchain.agents import initialize_agent, Tool
from langchain.agents import AgentType

llm = ChatOpenAI(
    model_name="llama-2-7b",
    openai_api_base="http://<device-ip>:11434/v1",
    openai_api_key="not-needed",
    temperature=0.7
)

tools = [
    # Add your tools here
]

agent = initialize_agent(
    tools,
    llm,
    agent=AgentType.CHAT_ZERO_SHOT_REACT_DESCRIPTION,
    verbose=True
)

agent.run("Solve this task...")
```

### Python Image Generation with Model Selection

```python
import requests
import base64

# First, list available image models
response = requests.get("http://<device-ip>:11434/v1/image-models")
models = response.json()
print("Available models:", [m['id'] for m in models['data']])

# Generate image with specific model
response = requests.post(
    "http://<device-ip>:11434/v1/images/generations",
    json={
        "prompt": "A futuristic city at night",
        "model": "stable-diffusion-1.5",
        "size": "512x512"
    }
)
result = response.json()
image_b64 = result["data"][0]["b64_json"]

# Save to file
with open("generated_image.jpg", "wb") as f:
    f.write(base64.b64decode(image_b64))
```

---

## Network Configuration

### Setup Steps

1. **Find Device IP**
   - On your Android device, go to Settings > About
   - Look for "IP address" (usually under Network or Device status)
   - Example: `192.168.1.100`

2. **Same Network Requirement**
   - Ensure your client machine is on the same WiFi network as the Android device
   - Both devices must be able to reach each other

3. **Enable Remote API**
   - Open ToolNeuron app on your Android device
   - Go to Settings/Preferences
   - Toggle "Remote API Access" to ON
   - Default port is 11434

4. **Network Discovery (Optional)**
   - If your network supports mDNS, use: `ToolNeuron-API.local`
   - Fallback to IP address if mDNS fails

5. **Firewall Configuration**
   - Ensure port 11434 is not blocked by device or network firewall
   - Check Android device firewall settings
   - Check router/network firewall if necessary

### Troubleshooting Connection Issues

| Issue | Solution |
|-------|----------|
| Connection refused | Ensure Remote API is enabled in ToolNeuron settings |
| Timeout | Check device and client are on same WiFi network |
| Port already in use | Change port in ToolNeuron settings or restart device |
| mDNS not working | Use IP address directly instead of `.local` domain |
| Firewall blocking | Check device firewall and router settings |

---

## Response Formats

### Success Responses (HTTP 200)

**Chat Completion (Non-streaming):**
```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1699564800,
  "model": "llama-2-7b",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Response text"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

**Chat Completion (Streaming):**
```
data: {"id":"...", "object":"chat.completion", ...}
data: [DONE]
```

### Error Responses

| HTTP Code | Description | Example |
|-----------|-------------|---------|
| 400 | Bad Request | Invalid JSON or missing required fields |
| 500 | Internal Server Error | Engine not initialized or generation failed |

**Error Response Format:**
```json
{
  "error": "Error message describing the problem"
}
```

---

## Performance Optimization

### Streaming vs Non-streaming

**Use Streaming when:**
- You want real-time token output
- Building interactive chat interfaces
- User experience is critical
- Token count is unpredictable

**Use Non-streaming when:**
- You need the full response at once
- Automating batch processing
- Simple integration scripts

### Model Selection

- **Smaller models (3-7B)**: Faster, ~2-10 tokens/sec on modern devices
- **Larger models (13-70B)**: Slower, ~0.5-2 tokens/sec depending on device
- Test different models to find the balance between speed and quality

### Device Considerations

- **RAM**: More RAM allows larger models to run smoothly
- **Storage**: GGUF models consume 3-50GB depending on size and quantization
- **Processor**: Better processors significantly speed up inference
- **First Request**: Expect ~1-5 second delay as model loads into memory

### Request Optimization

```python
# ✅ Good: Streaming for interactive use
response = client.chat.completions.create(
    model="llama-2-7b",
    messages=[...],
    stream=True,
    max_tokens=256  # Reasonable limit
)

# ❌ Avoid: Very high token limits
max_tokens=8192  # Too high, may cause memory issues

# ❌ Avoid: Frequent health checks
# Cache model list, don't call /v1/models every request
```

---

## Security Considerations

### Important Notes

⚠️ **Local Network Only**: The API is designed for local network access only. It's not recommended to expose to the internet without proper authentication.

⚠️ **No API Key**: The API doesn't require authentication for local network access. Ensure your network is secure.

⚠️ **Data Privacy**: All processing happens on device. No data is sent to external servers. However, ensure your local network is trusted.

### Best Practices

1. Only enable Remote API when needed
2. Disable when not in use to save battery
3. Use on trusted WiFi networks only
4. Consider network segmentation if concerned about security
5. Implement rate limiting in your client application

---

## Rate Limiting & Concurrency

- **Concurrent Requests**: The server can handle multiple simultaneous requests
- **No Hard Limit**: Limited only by device RAM and processing capacity
- **Recommended**: 2-4 concurrent requests for optimal performance
- **Streaming**: Each streaming connection consumes minimal bandwidth

---

## Common Patterns

### Conversation State Management

```python
messages = [
    {"role": "system", "content": "You are a helpful assistant."}
]

# First exchange
messages.append({"role": "user", "content": "Hello"})
response1 = client.chat.completions.create(
    model="llama-2-7b",
    messages=messages
)
messages.append({"role": "assistant", "content": response1.choices[0].message.content})

# Second exchange - context is preserved
messages.append({"role": "user", "content": "What did I just say?"})
response2 = client.chat.completions.create(
    model="llama-2-7b",
    messages=messages
)
```

### Error Handling

```python
import requests
from requests.exceptions import Timeout, ConnectionError

try:
    response = requests.post(
        "http://<device-ip>:11434/v1/chat/completions",
        json={...},
        timeout=120
    )
    response.raise_for_status()
    result = response.json()
except ConnectionError:
    print("Cannot connect to device. Check IP and network.")
except Timeout:
    print("Request timeout. Model may be processing large input.")
except requests.exceptions.HTTPError as e:
    print(f"API error: {e.response.status_code}")
```

### Batch Processing

```python
prompts = ["Explain X", "Summarize Y", "Generate Z"]
results = []

for prompt in prompts:
    response = client.chat.completions.create(
        model="llama-2-7b",
        messages=[{"role": "user", "content": prompt}],
        max_tokens=256
    )
    results.append(response.choices[0].message.content)

for prompt, result in zip(prompts, results):
    print(f"Q: {prompt}\nA: {result}\n")
```

---

## FAQ

**Q: Can I use the API over the internet?**
A: Not recommended. The API is designed for local network only. Exposing to the internet requires proper authentication and security measures.

**Q: Does the API work offline?**
A: The API works entirely offline once ToolNeuron is running. No internet connection required.

**Q: Can I use multiple models simultaneously?**
A: Not simultaneously on the same request. Switch between models by specifying different model IDs in requests.

**Q: What's the maximum response length?**
A: Limited by model context window and max_tokens parameter (typically 2048-4096 tokens).

**Q: Does streaming consume less bandwidth?**
A: Streaming and non-streaming use similar bandwidth. Streaming provides better UX by showing results as they're generated.

**Q: Can I interrupt a generation?**
A: Yes, close the connection. The server will stop processing.

**Q: How do I change the port?**
A: Open ToolNeuron Settings > Remote API > Port number.

**Q: Is the API persistent between app restarts?**
A: No, restart the ToolNeuron app and enable Remote API again to resume.

---

## Support & Feedback

- **Discord**: [Join Discord Community](https://discord.gg/mVPwHDhrAP)
- **GitHub Issues**: [Report Issues](https://github.com/Siddhesh2377/ToolNeuron/issues)
- **Documentation**: [Main README](../README.md)
