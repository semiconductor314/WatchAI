import router from '@system.router'

export default {
    openChat() {
        console.log("被点击了")
        router.push({
            uri: 'pages/chat/chat'
        })
    },
    openSettings() {
        // 设置页面预留
        console.log('设置页面待实现')
        // router.push({ uri: 'pages/settings/settings' })
    }
}