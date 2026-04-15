# OpenWebUI + ToolNeuron Integration Guide

Complete setup guide for using ToolNeuron with OpenWebUI.

## What is OpenWebUI?

OpenWebUI is a full-featured web interface for LLMs that supports multiple backends including OpenAI-compatible APIs. It provides a ChatGPT-like experience with advanced features like file uploads, code execution, and image generation.

**Website**: https://openwebui.com

## Prerequisites

1. **OpenWebUI installed and running**
   - Docker: `docker run -d -p 3000:8080 --name open-webui open-webui`
   - Or local installation from https://github.com/open-webui/open-webui

2. **ToolNeuron running with Remote API enabled**
   - Open ToolNeuron app on Android device
   - Settings → Enable "Remote API Access"
   - Note the device IP address

3. **Network connectivity**
   - Android device and OpenWebUI host on same WiFi network
   - Port 11434 accessible between devices

## Setup Instructions

### Step 1: Find Your Device IP

**On Android Device:**
1. Open Settings
2. About → Status (or About phone)
3. Look for "IP address" (usually under Network or Device status)
4. Example: `192.168.1.100`

**From Command Line (Windows):**
```cmd
ipconfig
```
Look for IPv4 Address under your WiFi adapter.

### Step 2: Access OpenWebUI

1. Open web browser
2. Navigate to `http://localhost:3000` (or your OpenWebUI server address)
3. Login or create account

### Step 3: Configure ToolNeuron Connection

#### Method 1: Settings → Admin Settings (Recommended)

1. Click **Settings** icon (⚙️) at bottom left
2. Select **Admin Settings** or **Connections**
3. Look for **"New Model"** or **"Connect Provider"**
4. Configure as follows:
   - **Provider/API Type**: OpenAI
   - **Base URL**: `http://192.168.1.100:11434/v1` (replace IP)
   - **API Key**: Leave empty or use `sk-test`
   - **Connection Name**: ToolNeuron
5. Click **Connect** or **Save**

#### Method 2: Settings → Models

1. Click **Settings** icon (⚙️)
2. Select **Models**
3. Click **➕ Add** or **New Connection**
4. Fill in same details as Method 1

### Step 4: Verify Connection

1. Wait 5-10 seconds for models to load
2. Go back to chat interface
3. Click model selector dropdown
4. Look for your ToolNeuron models (e.g., "llama-2-7b", "mistral-7b")

If models don't appear, check **Troubleshooting** section below.

## Using ToolNeuron in OpenWebUI

### Chat

1. Select a ToolNeuron model from the dropdown
2. Type your prompt
3. Chat as normal - streaming will work automatically

### Image Generation

1. Click the **image icon** in the text area
2. Type image prompt
3. Images will be generated using your configured image model

### Parameters

Adjust generation parameters in the chat interface:
- **Temperature**: Creativity level (0.0 = deterministic, 2.0 = very random)
- **Max Tokens**: Response length limit
- **Top P**: Nucleus sampling

## Troubleshooting

### "Cannot connect to server"

**Check:**
1. Device IP is correct
   ```bash
   # Test connectivity
   curl http://192.168.1.100:11434/health
   ```
   Should return: `{"status": "ok"}`

2. Remote API is enabled in ToolNeuron app

3. Firewall isn't blocking port 11434
   - Check device settings
   - Check network firewall

4. Both devices on same network
   - Ping test: `ping 192.168.1.100`

### No models appear

**Check:**
1. Models are loaded in ToolNeuron
   ```bash
   curl http://192.168.1.100:11434/v1/models
   ```
   Should return JSON list of models

2. Try URL with `/v1` path:
   - Use: `http://192.168.1.100:11434/v1`
   - Not: `http://192.168.1.100:11434`

3. Refresh OpenWebUI browser page (F5)

4. Check browser console for errors (F12)

### Slow responses

**Normal behavior:**
- First request to a model is slow (30-60 seconds) - model is loading
- Subsequent requests are faster (~1-5 seconds per token)

**To optimize:**
- Keep ToolNeuron app running
- Don't unload models between uses
- Increase OpenWebUI request timeout in settings

### "Unauthorized" or "Authentication failed"

**Solution:**
- API key is not required for local ToolNeuron
- Leave API Key field **empty**
- Or use dummy value like `sk-test`

### Different URL suggestions

If `http://IP:11434/v1` doesn't work, try:

| URL | Purpose |
|-----|---------|
| `http://IP:11434/v1` | Standard (recommended) |
| `http://IP:11434` | Base URL |
| `http://ToolNeuron-API.local/v1` | mDNS (if supported) |

## Advanced Configuration

### Proxy/Reverse Proxy

If accessing through reverse proxy:

```nginx
location /toolneuron/ {
    proxy_pass http://192.168.1.100:11434/;
    proxy_buffering off;
    proxy_request_buffering off;
}
```

Then use: `http://proxy-server/toolneuron/v1`

### Multiple Connections

You can configure multiple connections:
1. Different device IPs
2. Different model sets
3. One for chat, one for images

### HTTPS/SSL

For remote connections, use HTTPS:
1. Set up SSL proxy
2. Use: `https://your-domain/v1`

## Supported Features Matrix

| Feature | Status | Notes |
|---------|--------|-------|
| Chat Completions | ✅ Full | Streaming & non-streaming |
| Model Discovery | ✅ Full | Auto-detects all GGUF models |
| Health Checks | ✅ Full | Multiple endpoints |
| Image Generation | ✅ Full | With model selection |
| Streaming | ✅ Full | Real-time token streaming |
| Temperature Control | ✅ Full | Inference parameters |
| Max Tokens | ✅ Full | Response length control |
| System Prompt | ✅ Full | Via system role |
| File Upload | ⚠️ Partial | For context, not training |
| Web Search | ❌ No | Use ToolNeuron's plugin |
| Function Calling | ✅ Full | Tool calling supported |
| Vision/Images | ⚠️ Partial | Input: supported, Output: generation only |

## Performance Tips

### Mobile Device Optimization

1. **Keep app running**
   - Don't minimize ToolNeuron
   - Disable auto-sleep during sessions

2. **WiFi optimization**
   - Use 5GHz WiFi if available
   - Keep signal strong
   - Avoid network congestion

3. **Model selection**
   - Smaller models (3-7B) = faster responses
   - Larger models (13-70B) = better quality but slower

### OpenWebUI Optimization

1. **Increase timeout** in admin settings:
   - Request timeout: 180+ seconds (for first request)
   - Connection timeout: 30+ seconds

2. **Disable auto-save** for faster response times

3. **Use streaming** for better UX with long responses

## Security Considerations

⚠️ **Important:** This setup is for local network only.

**For internet-facing deployments:**
1. Use authentication (API key)
2. Use HTTPS/SSL
3. Use reverse proxy with authentication
4. Restrict IP addresses
5. Consider firewall rules

## API Endpoint Reference

All endpoints available through OpenWebUI:

```bash
# Health checks
GET /health
GET /healthz

# Models
GET /v1/models
GET /v1/models/{model_id}
GET /v1/image-models

# Chat
POST /v1/chat/completions

# Images
POST /v1/images/generations

# Info
GET /v1
GET /api/v1
```

See [REMOTE_API.md](REMOTE_API.md) for full API documentation.

## Common Use Cases

### Use Case 1: Quick Prototyping

**Setup:**
- OpenWebUI on laptop
- ToolNeuron on nearby Android phone
- Both on home WiFi

**Workflow:**
- Chat with models instantly
- Test prompts and parameters
- Generate images quickly

### Use Case 2: Development Environment

**Setup:**
- OpenWebUI in Docker on dev machine
- ToolNeuron on multiple Android devices
- Network isolated development environment

**Benefits:**
- Multiple models available
- No cloud dependencies
- Full data privacy
- Cost-free testing

### Use Case 3: Team Workspace

**Setup:**
- Shared OpenWebUI instance
- Multiple ToolNeuron devices
- Local network configuration

**Features:**
- Shared conversation history
- Load balancing across devices
- Redundancy

## Support & Resources

- **ToolNeuron GitHub**: https://github.com/Siddhesh2377/ToolNeuron
- **OpenWebUI GitHub**: https://github.com/open-webui/open-webui
- **ToolNeuron Discord**: https://discord.gg/mVPwHDhrAP
- **Issue Tracker**: https://github.com/Siddhesh2377/ToolNeuron/issues

## Frequently Asked Questions

**Q: Does ToolNeuron need to be on a rooted device?**
A: No, it works on any Android 12+ device. No root required.

**Q: Can I use it on mobile data / internet?**
A: Not recommended for security reasons. Local network only is best practice.

**Q: How many concurrent connections can OpenWebUI have?**
A: Limited only by device RAM and bandwidth. Typically 2-4 concurrent requests.

**Q: Can I use multiple image models?**
A: Yes! Switch models in OpenWebUI settings. ToolNeuron will load them automatically.

**Q: What happens if I uninstall ToolNeuron?**
A: Connections remain configured in OpenWebUI but fail gracefully. Reinstall to resume.

**Q: Can I run ToolNeuron on iOS?**
A: Currently Android only. iOS version in future roadmap.

## Next Steps

1. ✅ Install and enable Remote API in ToolNeuron
2. ✅ Install and configure OpenWebUI
3. ✅ Add ToolNeuron connection
4. ✅ Start chatting!
5. 📖 Read [REMOTE_API.md](REMOTE_API.md) for advanced usage

---

**Last Updated**: 2026-04-15  
**ToolNeuron Version**: 2.1.0+  
**Status**: ✅ Fully Tested
