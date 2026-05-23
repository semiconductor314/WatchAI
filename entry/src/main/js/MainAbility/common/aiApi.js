import fetch from '@system.fetch'

// 请替换为真实的AI API地址和密钥
const API_URL = 'https://api.openai.com/v1/chat/completions'
const API_KEY = 'YOUR_API_KEY_HERE'

export default {
    async chat(userMessage, conversationHistory = []) {
        // 构建消息历史
        const messages = [
            { role: 'system', content: '你是一个智能手表上的AI助手，回答要简洁、准确、友好。' },
            ...conversationHistory,
            { role: 'user', content: userMessage }
        ]

        // 使用华为AI API（可选）
        // 方式1：使用华为ML Kit文本对话API
        // 方式2：调用第三方AI服务

        // 示例：调用通用AI接口
        try {
            const response = await fetch.fetch({
                url: API_URL,
                method: 'POST',
                header: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${API_KEY}`
                },
                body: JSON.stringify({
                    model: 'gpt-3.5-turbo',
                    messages: messages,
                    max_tokens: 300,
                    temperature: 0.7
                })
            })

            const result = JSON.parse(response.data)
            return result.choices[0].message.content

        } catch (error) {
            console.error('API请求失败:', error)
            // 降级：本地模拟回复
            return this.getMockReply(userMessage)
        }
    },

    // 本地模拟回复（调试用）
    getMockReply(message) {
        if (message.includes('天气')) {
            return '今天天气晴朗，温度22°C，适合户外活动。'
        } else if (message.includes('时间')) {
            return `现在是${new Date().toLocaleTimeString()}`
        } else if (message.includes('你好')) {
            return '你好！有什么我能帮你的吗？'
        } else {
            return `收到："${message.length > 20 ? message.substring(0, 20) + '...' : message}"\n我是智能助手，正在努力学习中。`
        }
    }
}