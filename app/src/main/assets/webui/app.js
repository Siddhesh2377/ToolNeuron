// ToolNeuron Web UI App Logic

let currentTab = 'chat';
let isProcessing = false;
let chatHistory = [];

// DOM Elements
const views = {
    chat: document.getElementById('view-chat'),
    image: document.getElementById('view-image'),
    system: document.getElementById('view-system')
};

const tabs = {
    chat: document.getElementById('tab-chat'),
    image: document.getElementById('tab-image'),
    system: document.getElementById('tab-system')
};

const elements = {
    chatModel: document.getElementById('chat-model'),
    imageModel: document.getElementById('image-model'),
    chatMessages: document.getElementById('chat-messages'),
    chatInput: document.getElementById('chat-input'),
    sendBtn: document.getElementById('send-btn'),
    imagePrompt: document.getElementById('image-prompt'),
    imageSize: document.getElementById('image-size'),
    generateBtn: document.getElementById('generate-btn'),
    imageGallery: document.getElementById('image-gallery'),
    placeholder: document.getElementById('no-images-placeholder'),
    overlay: document.getElementById('loading-overlay'),
    loaderTitle: document.getElementById('loader-title'),
    sysCores: document.getElementById('sys-cores'),
    sysRamAvail: document.getElementById('sys-ram-avail'),
    sysRamTotal: document.getElementById('sys-ram-total'),
    runningModelsList: document.getElementById('running-models-list')
};

// ── Tab Management ──

window.showTab = function(tab) {
    currentTab = tab;
    
    // Update Tabs UI
    Object.keys(tabs).forEach(t => {
        if (t === tab) {
            tabs[t].classList.add('active');
            tabs[t].classList.remove('text-slate-400');
        } else {
            tabs[t].classList.remove('active');
            tabs[t].classList.add('text-slate-400');
        }
    });

    // Update Views UI
    Object.keys(views).forEach(v => {
        if (v === tab) {
            views[v].classList.remove('hidden');
        } else {
            views[v].classList.add('hidden');
        }
    });

    if (tab === 'system') {
        updateSystemInfo();
    }
};

// ── Initialization ──

async function init() {
    await fetchModels();
    
    // Auto-refresh system info if tab is active
    setInterval(() => {
        if (currentTab === 'system') updateSystemInfo();
    }, 3000);

    // Enter key for chat
    elements.chatInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    elements.sendBtn.onclick = sendMessage;
    elements.generateBtn.onclick = generateImage;
}

async function fetchModels() {
    try {
        // Fetch Chat Models
        const chatRes = await fetch('/v1/chat-models');
        const chatData = await chatRes.json();
        elements.chatModel.innerHTML = chatData.data.map(m => 
            `<option value="${m.id}">${m.id}</option>`
        ).join('') || '<option value="">No chat models found</option>';

        // Fetch Image Models
        const imgRes = await fetch('/v1/image-models');
        const imgData = await imgRes.json();
        elements.imageModel.innerHTML = imgData.data.map(m => 
            `<option value="${m.id}">${m.id}</option>`
        ).join('') || '<option value="">No image models found</option>';

    } catch (e) {
        console.error("Failed to fetch models", e);
    }
}

// ── Chat Logic ──

window.clearChat = function() {
    elements.chatMessages.innerHTML = '';
    chatHistory = [];
    addMessage('bot', 'Chat cleared. Ready for new session.');
};

async function sendMessage() {
    const text = elements.chatInput.value.trim();
    const model = elements.chatModel.value;
    
    console.log("Attempting to send message:", { text, model, isProcessing });

    if (!text || !model || isProcessing) {
        if (!model) alert("Please select a model first!");
        return;
    }

    elements.chatInput.value = '';
    addMessage('user', text);
    chatHistory.push({ role: 'user', content: text });

    isProcessing = true;
    elements.sendBtn.disabled = true;

    // Create assistant bubble for streaming
    const bubbleId = 'ai-' + Date.now();
    addMessage('bot', '', bubbleId);
    const bubbleContent = document.getElementById(bubbleId);
    
    try {
        console.log("Fetching chat completions...");
        const response = await fetch('/v1/chat/completions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: model,
                messages: chatHistory,
                stream: true
            })
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Server error (${response.status}): ${errorText}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let fullAiResponse = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            const chunk = decoder.decode(value);
            console.log("Received chunk:", chunk);
            const lines = chunk.split('\n');
            
            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const dataStr = line.substring(6).trim();
                    if (dataStr === '[DONE]') continue;
                    
                    try {
                        const data = JSON.parse(dataStr);
                        const content = (data.choices && data.choices[0].delta && data.choices[0].delta.content) || '';
                        fullAiResponse += content;
                        bubbleContent.innerText = fullAiResponse;
                        // Scroll to bottom
                        elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
                    } catch (e) {
                        console.warn("Failed to parse SSE line:", dataStr, e);
                    }
                }
            }
        }
        
        chatHistory.push({ role: 'assistant', content: fullAiResponse });

    } catch (e) {
        console.error("Chat error:", e);
        bubbleContent.innerText = "Error: " + e.message;
        bubbleContent.classList.add('text-red-400');
    } finally {
        isProcessing = false;
        elements.sendBtn.disabled = false;
    }
}

function addMessage(role, text, id = null) {
    const div = document.createElement('div');
    div.className = 'flex gap-4 ' + (role === 'user' ? 'flex-row-reverse' : '');
    
    const icon = role === 'user' ? 'user' : 'bot';
    const color = role === 'user' ? 'bg-slate-700' : 'bg-blue-600';
    
    div.innerHTML = `
        <div class="w-8 h-8 rounded-full ${color} flex items-center justify-center flex-shrink-0">
            <i data-lucide="${icon}" class="w-5 h-5 text-white"></i>
        </div>
        <div ${id ? `id="${id}"` : ''} class="chat-bubble ${role === 'user' ? 'bg-blue-700 rounded-tr-none' : 'bg-slate-900 rounded-tl-none border border-slate-800'} rounded-2xl p-4 text-slate-100 leading-relaxed whitespace-pre-wrap">
            ${text}
        </div>
    `;
    elements.chatMessages.appendChild(div);
    lucide.createIcons();
    elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
}

// ── Image Logic ──

async function generateImage() {
    const prompt = elements.imagePrompt.value.trim();
    const model = elements.imageModel.value;
    const size = elements.imageSize.value;

    console.log("Attempting to generate image:", { prompt, model, size, isProcessing });

    if (!prompt || !model || isProcessing) {
        if (!model) alert("Please select an image model first!");
        return;
    }

    setLoader(true, "Creating Image", "Stable Diffusion is generating your art...");
    
    try {
        console.log("Fetching image generations...");
        const res = await fetch('/v1/images/generations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt: prompt,
                model: model,
                size: size
            })
        });

        if (!res.ok) {
            const errorText = await res.text();
            throw new Error(`Server error (${res.status}): ${errorText}`);
        }

        const data = await res.json();
        
        if (data.data && data.data[0].b64_json) {
            addImageToGallery(data.data[0].b64_json, prompt);
            if (elements.placeholder) elements.placeholder.classList.add('hidden');
        } else {
            throw new Error(data.error || "Invalid response format from server");
        }

    } catch (e) {
        console.error("Image generation error:", e);
        alert("Image error: " + e.message);
    } finally {
        setLoader(false);
    }
}

function addImageToGallery(b64, prompt) {
    const div = document.createElement('div');
    div.className = 'group relative aspect-square bg-slate-900 rounded-3xl overflow-hidden border border-slate-800 hover:border-purple-500 transition-all cursor-pointer shadow-lg shadow-black/40';
    div.innerHTML = `
        <img src="data:image/jpeg;base64,${b64}" class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110" />
        <div class="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity p-4 flex flex-col justify-end">
            <p class="text-xs text-slate-300 line-clamp-2">${prompt}</p>
        </div>
    `;
    div.onclick = () => {
        const win = window.open();
        win.document.write(`<body style="margin:0;background:#0f172a;display:flex;align-items:center;justify-content:center;"><img src="data:image/jpeg;base64,${b64}" style="max-width:100%;max-height:100%;box-shadow:0 0 50px rgba(0,0,0,0.5)"></body>`);
    };
    elements.imageGallery.prepend(div);
}

// ── System Logic ──

async function updateSystemInfo() {
    try {
        const res = await fetch('/api/ps');
        const data = await res.json();

        if (data.hardware) {
            elements.sysCores.innerText = data.hardware.cpuCores;
            elements.sysRamAvail.innerText = data.hardware.availableRamMB;
            elements.sysRamTotal.innerText = data.hardware.totalRamMB;
        }

        if (data.models) {
            elements.runningModelsList.innerHTML = data.models.map(m => `
                <tr class="border-t border-slate-900 hover:bg-slate-900/50 transition-colors">
                    <td class="px-6 py-4 font-semibold text-blue-400">${m.name}</td>
                    <td class="px-6 py-4"><span class="bg-slate-800 px-2 py-0.5 rounded text-xs">${m.details.format}</span></td>
                    <td class="px-6 py-4 text-slate-400 text-sm">${m.details.family || '--'}</td>
                    <td class="px-6 py-4 text-slate-400 text-sm">${m.details.quantization_level || '--'}</td>
                    <td class="px-6 py-4"><span class="text-xs font-bold uppercase tracking-tighter ${m.details.backend === 'npu' ? 'text-emerald-500' : 'text-orange-400'}">${m.details.backend || 'NPU'}</span></td>
                </tr>
            `).join('');
            
            if (data.models.length === 0) {
                elements.runningModelsList.innerHTML = '<tr><td colspan="5" class="px-6 py-8 text-center text-slate-500 text-sm">No models currently in memory</td></tr>';
            }
        }
    } catch (e) {
        console.error("Dashboard update failed", e);
    }
}

// ── UI Helpers ──

function setLoader(show, title = "", subtitle = "") {
    isProcessing = show;
    if (show) {
        elements.loaderTitle.innerText = title;
        elements.loaderSubtitle.innerText = subtitle;
        elements.overlay.classList.remove('hidden');
        elements.overlay.classList.add('flex');
    } else {
        elements.overlay.classList.add('hidden');
        elements.overlay.classList.remove('flex');
    }
}

// Initialize on load
document.addEventListener('DOMContentLoaded', init);