import router from '@system.router';
import http from '@ohos.net.http';

export default {
    data: {
        messages: [],
        inputValue: '',
        isTyping: false,
        apiKey: '你的API Key',
        apiUrl: 'https://api.deepseek.com/v1/chat/completions'
    },

    onInit() {
        this.messages = [
            {
                role: 'assistant',
                content: '你好！我是AI助手，有什么可以帮你的吗？'
            }
        ];
    },

    // 返回首页
    backToHome() {
        console.log('返回按钮被点击');
        // @ts-ignore
        router.back();
    },

    onInputChange(e) {
        this.inputValue = e.value;
    },

    sendMessage() {
        let userMsg = this.inputValue;
        if (!userMsg || userMsg.trim() === '') {
            return;
        }

        this.messages.push({
            role: 'user',
            content: userMsg
        });

        this.inputValue = '';
        this.isTyping = true;

        this.callAIAPI(userMsg);
    },

    callAIAPI(userMessage) {
        let httpRequest = http.createHttp();

        let requestBody = {
            model: 'deepseek-chat',
            messages: this.buildMessages(userMessage),
            temperature: 0.7,
            max_tokens: 500
        };

        httpRequest.request(
            this.apiUrl,
            {
                method: http.RequestMethod.POST,
                header: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + this.apiKey
                },
                extraData: JSON.stringify(requestBody),
                readTimeout: 60000
            },
            (err, data) => {
                if (!err && data.responseCode === 200) {
                    let response = JSON.parse(data.result);
                    let aiResponse = response.choices[0].message.content;
                    this.addAIResponse(aiResponse);
                } else {
                    console.error('API请求失败');
                    this.addAIResponse('抱歉，服务暂时不可用，请稍后再试。');
                }
                httpRequest.destroy();
            }
        );
    },

    buildMessages(currentUserMessage) {
        let history = [];

        history.push({
            role: 'system',
            content: '你是一个智能手表上的AI助手，回答需要简洁明了，适合手表小屏幕阅读。'
        });

        let startIdx = Math.max(0, this.messages.length - 9);
        for (let i = startIdx; i < this.messages.length; i++) {
            history.push({
                role: this.messages[i].role,
                content: this.messages[i].content
            });
        }

        history.push({
            role: 'user',
            content: currentUserMessage
        });

        return history;
    },

    addAIResponse(content) {
        this.isTyping = false;
        this.messages.push({
            role: 'assistant',
            content: content
        });
    }
}