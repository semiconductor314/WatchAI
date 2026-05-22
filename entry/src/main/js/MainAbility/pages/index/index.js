export default {
    data: {
        messages: [],
        inputValue: '',
        isTyping: false
    },

    onInit() {
        // 初始化欢迎消息
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

    // 发送消息（调试版：模拟AI回复）
    sendMessage() {
        let userMsg = this.inputValue;
        if (!userMsg || userMsg.trim() === '') {
            return;
        }

        // 添加用户消息
        this.messages.push({
            role: 'user',
            content: userMsg
        });

        // 清空输入框
        this.inputValue = '';
        // 显示AI正在输入
        this.isTyping = true;

        // 模拟AI回复（延迟1秒）
        this.simulateAIResponse(userMsg);
    },

    // 模拟AI回复（调试用）
    simulateAIResponse(userMsg) {
        setTimeout(() => {
            // 根据用户消息生成不同的模拟回复
            let reply = this.getMockReply(userMsg);
            this.addAIResponse(reply);
        }, 1000);
    },

    // 模拟回复内容
    getMockReply(userMsg) {
        let msg = userMsg.toLowerCase();

        if (msg.includes('你好') || msg.includes('您好')) {
            return '你好！很高兴为你服务。';
        } else if (msg.includes('天气')) {
            return '今天天气不错，温度25°C，适合户外活动。';
        } else if (msg.includes('时间') || msg.includes('几点')) {
            let now = new Date();
            return `现在是 ${now.getHours()}:${now.getMinutes()}。`;
        } else if (msg.includes('谢谢')) {
            return '不客气，有问题随时问我！';
        } else if (msg.includes('帮助')) {
            return '我可以回答问题、查询天气、提醒事项等，试试问我吧！';
        } else {
            // 默认回复：回显用户消息
            return `你说的是："${userMsg}"\n\n（这是调试回复，接入API后我会更智能！）`;
        }
    },

    // 添加AI响应
    addAIResponse(content) {
        this.isTyping = false;
        this.messages.push({
            role: 'assistant',
            content: content
        });
    },

    // 清空对话（可选功能）
    clearChat() {
        this.messages = [
            {
                role: 'assistant',
                content: '有什么可以帮忙的？'
            }
        ];
    }
}