package com.semi.watchai.data.model

import java.util.UUID

/** 一条系统提示词（内置"默认"提示词除外）。 */
data class Prompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String
)
