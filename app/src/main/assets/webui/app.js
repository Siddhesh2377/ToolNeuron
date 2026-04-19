// ToolNeuron Web UI App Logic

let currentTab = 'chat';
let isProcessing = false;
let chatHistory = [];
let elements = {};

// ── Tab Management ──

window.showTab = function(tab) {
    currentTab = tab;
    const tabs = {
        chat: document.getElementById('tab-chat'),
        image: document.getElementById('tab-image'),
        system: document.getElementById('tab-system')
    };
    const views = {
        chat: document.getElementById('view-chat'),
        image: document.getElementById('view-image'),
        system: document.getElementById('view-system')
    };
    
    Object.keys(tabs).forEach(t => {
        if (t === tab) {
            tabs[t].classList.add('active');
            tabs[t].classList.remove('text-slate-400');
        } else {
            tabs[t].classList.remove('active');
            tabs[t].classList.add('text-slate-400');
        }
    });

    Object.keys(views).forEach(v => {
        if (v === tab) {
            views[v].classList.remove('hidden');
        } else {
            views[v].classList.add('hidden');
        }
    });

    if (tab === 'system') updateSystemInfo();
};

// ── Initialization ──

async function init() {
    console.log("WebUI Initializing...");
    
    // Initialize elements inside init to ensure DOM is ready
    elements = {
        chatModel: document.getElementById('chat-model'),
        imageModel: document.getElementById('image-model'),
        chatMessages: document.getElementById('chat-messages'),
        chatInput: document.getElementById('chat-input'),
        sendBtn: document.getElementById('send-btn'),
        imagePrompt: document.getElementById('image-prompt'),
        imageSize: document.getElementById('image-size'),
        imageStepsNum: document.getElementById('image-steps-num'),
        imageStepsSlider: document.getElementById('image-steps-slider'),
        imageStepsMaxLabel: document.getElementById('image-steps-max-label'),
        generateBtn: document.getElementById('generate-btn'),
        imageGallery: document.getElementById('image-gallery'),
        placeholder: document.getElementById('no-images-placeholder'),
        overlay: document.getElementById('loading-overlay'),
        loaderTitle: document.getElementById('loader-title'),
        loaderSubtitle: document.getElementById('loader-subtitle'),
        sysCores: document.getElementById('sys-cores'),
        sysRamAvail: document.getElementById('sys-ram-avail'),
        sysRamTotal: document.getElementById('sys-ram-total'),
        runningModelsList: document.getElementById('running-models-list')
    };

    if (!window.BASE_URL) {
        window.BASE_URL = window.location.origin;
    }
    
    await fetchModels();
    
    setInterval(() => {
        if (currentTab === 'system') updateSystemInfo();
    }, 3000);

    // Auto-resize chat input
    if (elements.chatInput) {
        elements.chatInput.addEventListener('input', function() {
            this.style.height = 'auto';
            this.style.height = (this.scrollHeight) + 'px';
        });

        elements.chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    if (elements.sendBtn) elements.sendBtn.onclick = sendMessage;
    if (elements.generateBtn) elements.generateBtn.onclick = generateImage;

    // Iterations sync
    if (elements.imageStepsNum && elements.imageStepsSlider) {
        elements.imageStepsSlider.addEventListener('input', (e) => {
            elements.imageStepsNum.value = e.target.value;
        });

        elements.imageStepsNum.addEventListener('input', (e) => {
            let val = parseInt(e.target.value) || 1;
            if (val < 1) val = 1;
            if (val > 100) val = 100;
            e.target.value = val;

            // If num > slider max, update slider max
            if (val > parseInt(elements.imageStepsSlider.max)) {
                elements.imageStepsSlider.max = val;
                elements.imageStepsMaxLabel.innerText = val;
            }
            elements.imageStepsSlider.value = val;
        });
    }
    
    console.log("WebUI Ready. Handlers attached.");
}

async function fetchModels() {
    try {
        const baseUrl = window.BASE_URL || '';
        const chatRes = await fetch(`${baseUrl}/v1/chat-models`);
        const chatData = await chatRes.json();
        elements.chatModel.innerHTML = chatData.data.map(m => 
            `<option value="${m.id}">${m.id}</option>`
        ).join('') || '<option value="">No chat models found</option>';

        const imgRes = await fetch(`${baseUrl}/v1/image-models`);
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
    
    if (!text || !model || isProcessing) {
        if (!model) alert("Please select a model first!");
        return;
    }

    elements.chatInput.value = '';
    elements.chatInput.style.height = 'auto';
    addMessage('user', text);
    chatHistory.push({ role: 'user', content: text });

    isProcessing = true;
    elements.sendBtn.disabled = true;

    const bubbleId = 'ai-' + Date.now();
    addMessage('bot', '...', bubbleId);
    const bubbleContent = document.getElementById(bubbleId);
    
    try {
        const baseUrl = window.BASE_URL || '';
        const response = await fetch(`${baseUrl}/v1/chat/completions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                model: model,
                messages: chatHistory,
                stream: true
            })
        });

        if (!response.ok) {
            const err = await response.text();
            throw new Error(`HTTP ${response.status}: ${err}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let fullAiResponse = '';
        let buffer = '';
        let performance = null;

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            
            let lines = buffer.split('\n');
            buffer = lines.pop(); 

            for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed || trimmed === 'data: [DONE]') continue;
                
                if (trimmed.startsWith('data: ')) {
                    try {
                        const dataStr = trimmed.substring(6);
                        const data = JSON.parse(dataStr);
                        
                        if (data.choices && data.choices.length > 0 && data.choices[0].delta) {
                            const content = data.choices[0].delta.content || '';
                            if (fullAiResponse === '...') fullAiResponse = '';
                            fullAiResponse += content;
                            bubbleContent.innerText = fullAiResponse;
                        }

                        if (data.performance) {
                            performance = data.performance;
                        }

                        elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
                    } catch (e) {
                        console.warn("SSE parse error:", e);
                    }
                }
            }
        }
        
        chatHistory.push({ role: 'assistant', content: fullAiResponse });

        if (performance) {
            addPerformanceIcon(bubbleId, performance);
        }

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
    const msgId = id || Date.now();
    div.id = 'msg-wrapper-' + msgId;
    div.className = 'flex flex-col ' + (role === 'user' ? 'items-end' : 'items-start');
    
    const contentDiv = document.createElement('div');
    contentDiv.className = 'flex gap-4 ' + (role === 'user' ? 'flex-row-reverse' : '');
    
    const icon = role === 'user' ? 'user' : 'bot';
    const color = role === 'user' ? 'bg-slate-700' : 'bg-blue-600';
    
    contentDiv.innerHTML = `
        <div class="w-8 h-8 rounded-full ${color} flex items-center justify-center flex-shrink-0">
            <i data-lucide="${icon}" class="w-5 h-5 text-white"></i>
        </div>
        <div ${id ? `id="${id}"` : ''} class="chat-bubble ${role === 'user' ? 'bg-blue-700 rounded-tr-none' : 'bg-slate-900 rounded-tl-none border border-slate-800'} rounded-2xl p-4 text-slate-100 leading-relaxed whitespace-pre-wrap">
            ${text}
        </div>
    `;
    div.appendChild(contentDiv);
    elements.chatMessages.appendChild(div);
    if (window.lucide) lucide.createIcons();
    elements.chatMessages.scrollTop = elements.chatMessages.scrollHeight;
}

function addPerformanceIcon(bubbleId, perf) {
    const bubble = document.getElementById(bubbleId);
    if (!bubble) return;
    
    const wrapper = bubble.parentElement.parentElement;
    
    const infoDiv = document.createElement('div');
    infoDiv.className = 'mt-1 ml-12 flex items-center gap-1 text-slate-500 perf-trigger relative cursor-help';
    infoDiv.innerHTML = `
        <i data-lucide="info" class="w-3 h-3"></i>
        <span class="text-[10px] font-medium">${perf.tokens_per_second.toFixed(1)} t/s</span>
        
        <div class="perf-popover">
            <div class="space-y-2">
                <div class="flex justify-between border-b border-slate-700 pb-1 mb-1">
                    <span class="font-bold text-blue-400">Inference Info</span>
                </div>
                <div class="flex justify-between">
                    <span>Speed:</span>
                    <span class="text-white">${perf.tokens_per_second.toFixed(2)} t/s</span>
                </div>
                <div class="flex justify-between">
                    <span>Total Time:</span>
                    <span class="text-white">${(perf.total_time_ms / 1000).toFixed(2)}s</span>
                </div>
                <div class="flex justify-between">
                    <span>Tokens:</span>
                    <span class="text-white">${perf.completion_tokens} gen / ${perf.prompt_tokens} prompt</span>
                </div>
            </div>
        </div>
    `;
    wrapper.appendChild(infoDiv);
    if (window.lucide) lucide.createIcons();
}

// ── Image Logic ──

async function generateImage() {
    const prompt = elements.imagePrompt.value.trim();
    const model = elements.imageModel.value;
    const size = elements.imageSize.value;
    const steps = parseInt(elements.imageStepsNum.value) || 20;

    if (!prompt || !model || isProcessing) {
        if (!model) alert("Please select an image model first!");
        return;
    }

    setLoader(true, "Creating Image", "Stable Diffusion is generating your art...");
    
    try {
        const baseUrl = window.BASE_URL || '';
        const res = await fetch(`${baseUrl}/v1/images/generations`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt: prompt,
                model: model,
                size: size,
                steps: steps
            })
        });

        if (!res.ok) {
            const err = await res.text();
            throw new Error(`HTTP ${res.status}: ${err}`);
        }

        const data = await res.json();
        
        if (data.data && data.data[0].b64_json) {
            addImageToGallery(data.data[0].b64_json, prompt);
            if (elements.placeholder) elements.placeholder.classList.add('hidden');
        } else {
            throw new Error(data.error || "No image data returned");
        }

    } catch (e) {
        console.error("Image generation error:", e);
        alert("Image error: " + e.message);
    } finally {
        setLoader(false);
    }
}

window.closeImageOverlay = function() {
    const overlay = document.getElementById('image-overlay');
    if (overlay) overlay.classList.add('hidden');
};

function openImageOverlay(src) {
    const overlay = document.getElementById('image-overlay');
    const img = document.getElementById('overlay-img');
    if (overlay && img) {
        img.src = src;
        overlay.classList.remove('hidden');
    }
}

function addImageToGallery(b64, prompt) {
    const div = document.createElement('div');
    const src = `data:image/jpeg;base64,${b64}`;
    div.className = 'group relative aspect-square bg-slate-900 rounded-3xl overflow-hidden border border-slate-800 hover:border-purple-500 transition-all cursor-pointer shadow-lg shadow-black/40';
    div.innerHTML = `
        <img src="${src}" class="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110" />
        <div class="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity p-4 flex flex-col justify-end">
            <p class="text-xs text-slate-300 line-clamp-2">${prompt}</p>
        </div>
    `;
    div.onclick = () => openImageOverlay(src);
    elements.imageGallery.prepend(div);
}

// ── System Logic ──

async function updateSystemInfo() {
    try {
        const baseUrl = window.BASE_URL || '';
        const res = await fetch(`${baseUrl}/api/ps`);
        const data = await res.json();

        if (data.hardware) {
            elements.sysCores.innerText = data.hardware.cpuCores || '--';
            elements.sysRamAvail.innerText = data.hardware.availableRamMB || '--';
            elements.sysRamTotal.innerText = data.hardware.totalRamMB || '--';
        }

        if (data.models) {
            elements.runningModelsList.innerHTML = data.models.map(m => {
                const statusColor = getStatusColor(m.status);
                return `
                <tr class="border-t border-slate-900 hover:bg-slate-900/50 transition-colors">
                    <td class="px-6 py-4">
                        <div class="font-semibold text-blue-400">${m.name}</div>
                        ${m.error ? `<div class="text-[10px] text-red-500 mt-1 truncate max-w-[200px]" title="${m.error}">${m.error}</div>` : ''}
                    </td>
                    <td class="px-6 py-4"><span class="bg-slate-800 px-2 py-0.5 rounded text-xs">${m.details.format}</span></td>
                    <td class="px-6 py-4 text-slate-400 text-sm">${m.details.family || '--'}</td>
                    <td class="px-6 py-4 text-slate-400 text-sm">${m.details.quantization_level || '--'}</td>
                    <td class="px-6 py-4">
                        <span class="px-2 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ${statusColor}">
                            ${m.status}
                        </span>
                    </td>
                    <td class="px-6 py-4"><span class="text-xs font-bold uppercase tracking-tighter ${m.details.backend === 'npu' ? 'text-emerald-500' : 'text-orange-400'}">${m.details.backend || 'NPU'}</span></td>
                </tr>
            `}).join('');
            
            if (data.models.length === 0) {
                elements.runningModelsList.innerHTML = '<tr><td colspan="6" class="px-6 py-8 text-center text-slate-500 text-sm">No models currently in memory</td></tr>';
            }
        }
    } catch (e) {}
}

function getStatusColor(status) {
    switch (status.toLowerCase()) {
        case 'generating': return 'bg-emerald-500/10 text-emerald-500';
        case 'loading': return 'bg-blue-500/10 text-blue-500';
        case 'error': return 'bg-red-500/10 text-red-500';
        case 'idle': return 'bg-slate-500/10 text-slate-500';
        default: return 'bg-slate-500/10 text-slate-500';
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

window.setTestPrompt = function(prompt) {
    if (elements.chatInput && elements.chatInput.value.trim() === "") {
        elements.chatInput.value = prompt;
        elements.chatInput.focus();
        // Trigger resize
        elements.chatInput.dispatchEvent(new Event('input'));
    }
};

window.setImagePrompt = function(prompt) {
    if (elements.imagePrompt && elements.imagePrompt.value.trim() === "") {
        elements.imagePrompt.value = prompt;
        elements.imagePrompt.focus();
    }
};

// Initialize on load
document.addEventListener('DOMContentLoaded', init);