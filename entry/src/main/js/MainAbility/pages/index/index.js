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
                content: '有什么可以帮忙的？'
            }
        ];
    },

    // 点击输入区域，聚焦输入框
    focusInput() {
        let inputElement = this.$element('userInput');
        if (inputElement) {
            inputElement.focus();
        }
    },

    // 输入框获得焦点
    onInputFocus(e) {
        console.log('输入框已获得焦点');
    },

    // 输入框失去焦点
    onInputBlur(e) {
        console.log('输入框已失去焦点');
    },

    // 输入框内容变化
    onInputChange(e) {
        this.inputValue = e.value;
    },

    // 发送消息
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

    // 调用AI API
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
                    console.error('API请求失败: ' + JSON.stringify(err));
                    this.addAIResponse('抱歉，服务暂时不可用，请稍后再试。');
                }
                httpRequest.destroy();
            }
        );
    },

    // 构建消息历史
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

    // 添加AI响应
    addAIResponse(content) {
        this.isTyping = false;
        this.messages.push({
            role: 'assistant',
            content: content
        });
    }
}