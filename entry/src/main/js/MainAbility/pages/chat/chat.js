import router from '@system.router';

export default {
    data: {
        messages: [],
        inputValue: '',
        isTyping: false
    },

    onInit() {
        // 初始化测试数据
        this.messages = [
            { role: 'assistant', content: '你好！我是AI助手' },
            { role: 'user', content: '你好' },
            { role: 'assistant', content: '输入文字后点击发送，我会自动回复' }
        ];
        console.log('初始化完成，消息数量: ' + this.messages.length);
    },

    // 返回首页
    backToHome() {
        let stackLength = router.getLength();
        if (stackLength > 1) {
            router.back();
        } else {
            router.push({ uri: 'pages/index/index' });
        }
    },

    // 输入框变化
    onInputChange(e) {
        this.inputValue = e.value;
    },

    // 发送消息
    sendMessage() {
        let userMsg = this.inputValue;
        if (!userMsg || userMsg.trim() === '') {
            // 如果输入框为空，自动发送测试消息
            userMsg = '测试消息';
            this.inputValue = '';
        } else {
            this.inputValue = '';
        }

        // 添加用户消息
        this.messages.push({
            role: 'user',
            content: userMsg
        });

        // 显示AI正在输入
        this.isTyping = true;

        // 模拟AI回复
        setTimeout(() => {
            this.isTyping = false;
            let reply = this.getReply(userMsg);
            this.messages.push({
                role: 'assistant',
                content: reply
            });
        }, 600);
    },

    // 根据用户消息生成回复
    getReply(userMsg) {
        const msg = userMsg.toLowerCase();

        if (msg.includes('你好')) {
            return '你好！很高兴见到你！';
        }
        if (msg.includes('天气')) {
            return '今天天气不错，适合出门散步。';
        }
        if (msg.includes('谢谢')) {
            return '不客气！';
        }
        if (msg.includes('时间')) {
            return '现在是 ' + this.getCurrentTime();
        }
        if (msg === '测试消息') {
            return '收到测试消息！3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679821480865132823066470938446095505822317253594081284811174502841027019385211055596446229489549303819644288109756659334461284756482337867831652712019091456485669234603486104543266482133936072602491412737245870066063155881748815209209628292540917153643678925903600113305305488204665213841469519415116094330572703657595919530921861173819326117931051185480744623799627495673518857527248912279381830119491298336733624406566430860213949463952247371907021798609437027705392171762931767523846748184676694051320005681271452635608277857713427577896091736371787214684409012249534301465495853710507922796892';
        }

        return '收到：「' + userMsg + '」';
    },

    // 获取当前时间
    getCurrentTime() {
        let now = new Date();
        return now.getHours().toString().padStart(2, '0') + ':' +
        now.getMinutes().toString().padStart(2, '0');
    },

    // 清空对话
    clearChat() {
        this.messages = [
            { role: 'assistant', content: '对话已清空。你好！我是AI助手。' }
        ];
        this.isTyping = false;
        this.inputValue = '';
    }
}