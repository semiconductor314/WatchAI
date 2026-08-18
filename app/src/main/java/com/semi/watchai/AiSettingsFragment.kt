package com.semi.watchai

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * AI 设置通用 Fragment（首页全局设置页与对话设置页共用）。
 *
 * 首页 [allowManagement] = true：可新建提示词、新建供应商，提示词列表项右侧带 ":" 可编辑/删除；
 * 对话设置页 = false：隐藏管理按钮，只留下拉选择，改动经 [listener] 写进该对话。
 * 初始值经 [newInstance] 传入；值变化后通过 [listener] 交给宿主决定持久化到哪。
 */
class AiSettingsFragment : Fragment() {

    interface Listener {
        fun onProviderSelected(providerId: String)
        fun onPromptSelected(promptId: String)
        fun onModelOnlineChanged(enabled: Boolean)
        fun onStreamEnabledChanged(enabled: Boolean) {}
        fun onContextModeSelected(mode: String) {}
    }

    var listener: Listener? = null

    // 两个宿主 Activity 都实现了 Listener，直接在此绑定，保证设置能持久化
    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    private lateinit var providerRepository: ProviderRepository
    private lateinit var promptRepository: PromptRepository
    private lateinit var llProviderSelector: LinearLayout
    private lateinit var tvSelectedProvider: TextView
    private lateinit var llPromptSelector: LinearLayout
    private lateinit var tvSelectedPrompt: TextView
    private lateinit var switchModelOnline: Switch
    private lateinit var switchStreamEnabled: Switch
    private lateinit var spinnerContextMode: Spinner

    private val providers = mutableListOf<AiProvider>()
    private val prompts = mutableListOf<Prompt>()
    private var restoringSelection = false

    private var initialProviderId: String? = null
    private var initialModelOnline = false
    private var initialPromptId: String? = null
    private var allowManagement = true
    private var title = "AI 设置"
    private var showModelOnline = true
    private var showStreamEnabled = true
    private var showContextMode = true
    private var showPrompt = true
    private var showProvider = true

    // 上下文选择选项（显示名 -> 存储值），顺序即下拉顺序
    private val contextModeOptions = listOf(
        "包含全部上下文" to ProviderRepository.CONTEXT_MODE_ALL,
        "只包含上条上下文" to ProviderRepository.CONTEXT_MODE_LAST,
        "不包含上下文" to ProviderRepository.CONTEXT_MODE_NONE
    )

    private var initialContextMode = ProviderRepository.CONTEXT_MODE_ALL

    private var initialStreamEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_settings, container, false)

        providerRepository = ProviderRepository(requireContext())
        promptRepository = PromptRepository(requireContext())
        llProviderSelector = view.findViewById(R.id.llProviderSelector)
        tvSelectedProvider = view.findViewById(R.id.tvSelectedProvider)
        llPromptSelector = view.findViewById(R.id.llPromptSelector)
        tvSelectedPrompt = view.findViewById(R.id.tvSelectedPrompt)
        switchModelOnline = view.findViewById(R.id.switchModelOnline)
        switchStreamEnabled = view.findViewById(R.id.switchStreamEnabled)
        spinnerContextMode = view.findViewById(R.id.spinnerContextMode)

        arguments?.let {
            initialProviderId = it.getString(ARG_PROVIDER_ID)
            initialModelOnline = it.getBoolean(ARG_MODEL_ONLINE, false)
            initialPromptId = it.getString(ARG_PROMPT_ID)
            allowManagement = it.getBoolean(ARG_ALLOW_MANAGEMENT, true)
            initialContextMode = it.getString(ARG_CONTEXT_MODE) ?: ProviderRepository.CONTEXT_MODE_ALL
            initialStreamEnabled = it.getBoolean(ARG_STREAM_ENABLED, true)
            title = it.getString(ARG_TITLE) ?: "AI 设置"
            showModelOnline = it.getBoolean(ARG_SHOW_MODEL_ONLINE, true)
            showStreamEnabled = it.getBoolean(ARG_SHOW_STREAM_ENABLED, true)
            showContextMode = it.getBoolean(ARG_SHOW_CONTEXT_MODE, true)
            showPrompt = it.getBoolean(ARG_SHOW_PROMPT, true)
            showProvider = it.getBoolean(ARG_SHOW_PROVIDER, true)
        }

        // 按页面参数显隐区块 + 设置标题
        view.findViewById<TextView>(R.id.tvSettingsTitle).text = title
        refreshModelOnlineRowVisibility(view)
        view.findViewById<View>(R.id.llStreamEnabledRow).visibility =
            if (showStreamEnabled) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.llContextModeSection).visibility =
            if (showContextMode) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.llPromptSection).visibility =
            if (showPrompt) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.llProviderSection).visibility =
            if (showProvider) View.VISIBLE else View.GONE

        // 对话设置页不显示管理按钮（新建提示词 / 新建供应商）
        if (!allowManagement) {
            view.findViewById<View>(R.id.btnAddProvider).visibility = View.GONE
            view.findViewById<View>(R.id.btnAddPrompt).visibility = View.GONE
        }

        // 先设初始状态再注册监听，避免初始化时误触发保存
        switchModelOnline.isChecked = initialModelOnline
        switchModelOnline.setOnCheckedChangeListener { _, checked ->
            listener?.onModelOnlineChanged(checked)
        }

        switchStreamEnabled.isChecked = initialStreamEnabled
        switchStreamEnabled.setOnCheckedChangeListener { _, checked ->
            listener?.onStreamEnabledChanged(checked)
        }

        view.findViewById<Button>(R.id.btnAddProvider).setOnClickListener {
            showProviderDialog(edit = null)
        }

        view.findViewById<Button>(R.id.btnAddPrompt).setOnClickListener {
            showPromptDialog(edit = null)
        }

        // 手表滚轮（rotary encoder）滚动设置页
        val scrollView = view.findViewById<ScrollView>(R.id.scrollAiSettings)
        scrollView.setOnGenericMotionListener { _, event ->
            if (event.action == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
            ) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 50f
                scrollView.scrollBy(0, delta.toInt())
                true
            } else {
                false
            }
        }
        // 与首页历史记录一致：滚动容器必须持有焦点，滚轮事件才会派发到它
        scrollView.isFocusable = true
        scrollView.isFocusableInTouchMode = true
        scrollView.requestFocus()
        view.post { scrollView.requestFocus() }

        // 点击选择器 = 弹出提示词列表
        llPromptSelector.setOnClickListener {
            showPromptSelectorPopup()
        }

        // 点击选择器 = 弹出供应商列表（首页可管理：列表项右侧带 ":"）
        llProviderSelector.setOnClickListener {
            showProviderSelectorPopup()
        }

        spinnerContextMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (restoringSelection) return
                    if (position in contextModeOptions.indices) {
                        listener?.onContextModeSelected(contextModeOptions[position].second)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        refreshContextModeSpinner()
        loadProviders()
        loadPrompts()
        return view
    }

    /**
     * 小屏/圆表盘上 AlertDialog 内容超高时，底部按钮会被窗口裁掉（看不到保存键）。
     * 若内容超过屏幕可用高度的 85%，就把窗口高度限制在该值，让 ScrollView 可滚动，保证按钮可达。
     */
    private fun clampDialogHeight(dialog: androidx.appcompat.app.AlertDialog, contentView: View) {
        val win = dialog.window ?: return
        val dm = Resources.getSystem().displayMetrics
        val maxHeight = (dm.heightPixels * 0.85f).toInt()
        contentView.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )
        if (contentView.measuredHeight > maxHeight) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, maxHeight)
        }
    }

    // ========== 供应商 ==========

    private fun loadProviders() {
        lifecycleScope.launch {
            providers.clear()
            providers.addAll(providerRepository.providers())
            refreshProviderDisplay()
            view?.let { refreshModelOnlineRowVisibility(it) }
        }
    }

    private fun refreshProviderDisplay() {
        val target = initialProviderId
        tvSelectedProvider.text = providers.firstOrNull { it.id == target }?.name
            ?: providers.firstOrNull()?.name
            ?: "未配置"
    }

    private fun refreshContextModeSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            contextModeOptions.map { it.first }
        )
        adapter.setDropDownViewResource(R.layout.spinner_item)

        // 恢复选中时不触发保存逻辑（onItemSelected 同步回调）
        restoringSelection = true
        spinnerContextMode.adapter = adapter
        val index = contextModeOptions.indexOfFirst { it.second == initialContextMode }
        if (index >= 0) {
            spinnerContextMode.setSelection(index)
        }
        restoringSelection = false
    }

    private var providerListPopup: PopupWindow? = null

    /**
     * "模型联网"开关仅在当前供应商的模型支持联网搜索（目前仅 DeepSeek）时显示；
     * 供应商切换时联动刷新。
     */
    private fun refreshModelOnlineRowVisibility(root: View) {
        val provider = providers.firstOrNull { it.id == initialProviderId } ?: providers.firstOrNull()
        root.findViewById<View>(R.id.llModelOnlineRow).visibility =
            if (showModelOnline && provider?.supportsWebSearch() == true) View.VISIBLE else View.GONE
    }

    /** 选择模型预设应用到弹窗：预设模型自动填链接并置灰只读；自定义恢复可编辑（保留已填内容）。 */
    private fun applyProviderModelPreset(etUrl: EditText, info: AiModels.AiModelInfo, fillUrl: Boolean) {
        if (fillUrl && info.presetBaseUrl != null) {
            etUrl.setText(info.presetBaseUrl)
        }
        val preset = info.presetBaseUrl != null
        etUrl.isEnabled = !preset
        etUrl.alpha = if (preset) 0.5f else 1f
    }

    /** 点击选择器：弹出供应商列表（仿首页历史记录/提示词列表，列表项右侧带 ":"）。 */
    private fun showProviderSelectorPopup() {
        if (providers.isEmpty()) return

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val density = Resources.getSystem().displayMetrics.density
        // 宽 75% 以内，避免圆表盘边缘裁掉右侧 ":" 按钮
        val popupWidth = (screenWidth * 0.75f).toInt()
        // 行高按 dp 计算（48dp），避免像素换算后在低密度屏上过矮
        val rowHeight = (48 * density).toInt().coerceAtLeast(56)
        val maxRows = 4
        val popupHeight = (rowHeight * providers.size.coerceAtMost(maxRows) + 8)
            .coerceAtMost((screenHeight * 0.7f).toInt())

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_provider_list, null)
        val container = popupView.findViewById<LinearLayout>(R.id.providerListContainer)

        providers.forEach { provider ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_provider_selector, container, false)
            val tvName = row.findViewById<TextView>(R.id.tvProviderSelectorName)
            val btnAction = row.findViewById<Button>(R.id.btnProviderSelectorAction)

            tvName.text = provider.name
            tvName.layoutParams = LinearLayout.LayoutParams(0, rowHeight, 1f)
            btnAction.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight
            )

            // 点击条目 = 选中该供应商
            row.setOnClickListener {
                providerListPopup?.dismiss()
                providerListPopup = null
                selectProvider(provider)
            }

            // 正在使用的供应商不可编辑/删除；对话设置页不提供管理：都不显示 ":"
            val isInUse = provider.id == initialProviderId
            if (!allowManagement || isInUse) {
                btnAction.visibility = View.INVISIBLE
            } else {
                btnAction.setOnClickListener {
                    // 先捕获位置再解散列表，否则锚点已脱离屏幕（读到 (0,0)，弹窗跑到左上角）
                    val loc = IntArray(2)
                    btnAction.getLocationOnScreen(loc)
                    providerListPopup?.dismiss()
                    providerListPopup = null
                    showProviderActions(provider, loc)
                }
            }
            container.addView(row)
        }

        val popup = PopupWindow(popupView, popupWidth, popupHeight, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.animationStyle = android.R.style.Animation_Dialog
        popup.setOutsideTouchable(true)
        popupView.setOnTouchListener { _, _ ->
            popup.dismiss()
            providerListPopup = null
            false
        }
        providerListPopup = popup
        popup.showAtLocation(llProviderSelector, Gravity.CENTER, 0, 0)
    }

    private fun selectProvider(provider: AiProvider) {
        initialProviderId = provider.id
        refreshProviderDisplay()
        view?.let { refreshModelOnlineRowVisibility(it) }
        listener?.onProviderSelected(provider.id)
    }

    /** 新建/编辑供应商弹窗（编辑时预填字段，标题切换为"编辑供应商"）。 */
    private fun showProviderDialog(edit: AiProvider?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_provider, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvProviderDialogTitle)
        val etName = dialogView.findViewById<EditText>(R.id.etProviderName)
        val etUrl = dialogView.findViewById<EditText>(R.id.etProviderUrl)
        val etKey = dialogView.findViewById<EditText>(R.id.etProviderKey)
        val spinnerModel = dialogView.findViewById<Spinner>(R.id.spinnerProviderModel)

        if (edit != null) {
            tvTitle.text = "编辑供应商"
            etName.setText(edit.name)
            etUrl.setText(edit.baseUrl)
            etKey.setText(edit.apiKey)
        }

        // 选择模型下拉：预设模型（DeepSeek）自动填链接并置灰只读，自定义恢复手填
        var selectedModel = edit?.model ?: AiModels.CUSTOM
        val modelAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            AiModels.ALL.map { it.displayName }
        )
        modelAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinnerModel.adapter = modelAdapter

        // 初始化选中项时抑制误触发自动填链接（编辑已有供应商时保留其 URL）
        var restoringSelection = true
        val modelIndex = AiModels.ALL.indexOfFirst { it.id == selectedModel }.coerceAtLeast(0)
        spinnerModel.setSelection(modelIndex)
        restoringSelection = false
        // 打开即按模型应用只读状态（不覆盖已存 URL）
        applyProviderModelPreset(etUrl, AiModels.byId(selectedModel), fillUrl = false)

        spinnerModel.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (restoringSelection) return
                    if (position in AiModels.ALL.indices) {
                        selectedModel = AiModels.ALL[position].id
                        applyProviderModelPreset(etUrl, AiModels.ALL[position], fillUrl = true)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val url = etUrl.text.toString().trim()
            val key = etKey.text.toString().trim()
            if (name.isEmpty() || url.isEmpty() || key.isEmpty()) {
                Toast.makeText(requireContext(), "名称、链接、Key 都不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            if (edit != null) {
                updateProvider(edit.copy(name = name, baseUrl = url, apiKey = key, model = selectedModel))
            } else {
                addProvider(AiProvider(name = name, baseUrl = url, apiKey = key, model = selectedModel))
            }
        }

        dialog.show()
        clampDialogHeight(dialog, dialogView)
    }

    private fun addProvider(provider: AiProvider) {
        lifecycleScope.launch {
            providerRepository.addProvider(provider)
            initialProviderId = provider.id
            loadProviders()
            // 让宿主同步持久化（对话设置页 = 该对话用新供应商）
            listener?.onProviderSelected(provider.id)
            Toast.makeText(requireContext(), "已添加供应商：${provider.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateProvider(provider: AiProvider) {
        lifecycleScope.launch {
            providerRepository.updateProvider(provider)
            initialProviderId = provider.id
            loadProviders()
            // 编辑后选中该供应商（与提示词编辑行为一致），让宿主同步持久化
            listener?.onProviderSelected(provider.id)
            Toast.makeText(requireContext(), "已更新供应商：${provider.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteProvider(provider: AiProvider) {
        lifecycleScope.launch {
            providerRepository.deleteProvider(provider.id)
            // 正在使用的供应商不允许删除（":" 不可见），理论上到不了这里；
            // 兜底：若选中项意外失效则回退到第一个
            if (initialProviderId == provider.id) {
                val fallback = providerRepository.providers().firstOrNull()
                initialProviderId = fallback?.id
                if (fallback != null) {
                    listener?.onProviderSelected(fallback.id)
                }
            }
            loadProviders()
            Toast.makeText(requireContext(), "已删除供应商：${provider.name}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 提示词（选择器 + ":" 编辑/删除） ==========

    private var promptListPopup: PopupWindow? = null

    private fun loadPrompts() {
        lifecycleScope.launch {
            prompts.clear()
            prompts.addAll(promptRepository.allPrompts())
            refreshPromptDisplay()
        }
    }

    private fun refreshPromptDisplay() {
        val target = initialPromptId ?: PromptRepository.DEFAULT_PROMPT_ID
        tvSelectedPrompt.text = prompts.firstOrNull { it.id == target }?.name
            ?: prompts.firstOrNull()?.name
            ?: ""
    }

    /** 点击选择器：弹出提示词列表（仿首页历史记录，列表项右侧带 ":"）。 */
    private fun showPromptSelectorPopup() {
        if (prompts.isEmpty()) return

        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val density = Resources.getSystem().displayMetrics.density
        // 宽 75% 以内，避免圆表盘边缘裁掉右侧 ":" 按钮
        val popupWidth = (screenWidth * 0.75f).toInt()
        // 行高按 dp 计算（48dp），避免像素换算后在低密度屏上过矮
        val rowHeight = (48 * density).toInt().coerceAtLeast(56)
        val maxRows = 4
        val popupHeight = (rowHeight * prompts.size.coerceAtMost(maxRows) + 8)
            .coerceAtMost((screenHeight * 0.7f).toInt())

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_prompt_list, null)
        val container = popupView.findViewById<LinearLayout>(R.id.promptListContainer)

        prompts.forEach { prompt ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_prompt_selector, container, false)
            val tvName = row.findViewById<TextView>(R.id.tvPromptSelectorName)
            val btnAction = row.findViewById<Button>(R.id.btnPromptSelectorAction)

            tvName.text = prompt.name
            tvName.layoutParams = LinearLayout.LayoutParams(0, rowHeight, 1f)
            btnAction.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, rowHeight
            )

            // 点击条目 = 选中该提示词
            row.setOnClickListener {
                promptListPopup?.dismiss()
                promptListPopup = null
                selectPrompt(prompt)
            }

            // "默认"不可编辑/删除：不显示 ":"
            if (prompt.id == PromptRepository.DEFAULT_PROMPT_ID) {
                btnAction.visibility = View.INVISIBLE
            } else {
                btnAction.setOnClickListener {
                    // 先捕获位置再解散列表，否则锚点已脱离屏幕（读到 (0,0)，弹窗跑到左上角）
                    val loc = IntArray(2)
                    btnAction.getLocationOnScreen(loc)
                    promptListPopup?.dismiss()
                    promptListPopup = null
                    showPromptActions(prompt, loc)
                }
            }
            container.addView(row)
        }

        val popup = PopupWindow(popupView, popupWidth, popupHeight, true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.animationStyle = android.R.style.Animation_Dialog
        popup.setOutsideTouchable(true)
        popupView.setOnTouchListener { _, _ ->
            popup.dismiss()
            promptListPopup = null
            false
        }
        promptListPopup = popup
        popup.showAtLocation(llPromptSelector, Gravity.CENTER, 0, 0)
    }

    private fun selectPrompt(prompt: Prompt) {
        initialPromptId = prompt.id
        refreshPromptDisplay()
        listener?.onPromptSelected(prompt.id)
    }

    /** 提示词 ":" 弹窗：编辑 / 删除。 */
    private fun showPromptActions(prompt: Prompt, anchorLocation: IntArray) {
        showActionsPopup(
            anchorView = llPromptSelector,
            anchorLocation = anchorLocation,
            option1Text = "编辑",
            option2Text = "删除",
            onOption1 = { showPromptDialog(edit = prompt) },
            onOption2 = { deletePrompt(prompt) }
        )
    }

    /** 供应商 ":" 弹窗：编辑 / 删除。 */
    private fun showProviderActions(provider: AiProvider, anchorLocation: IntArray) {
        showActionsPopup(
            anchorView = llProviderSelector,
            anchorLocation = anchorLocation,
            option1Text = "编辑",
            option2Text = "删除",
            onOption1 = { showProviderDialog(edit = provider) },
            onOption2 = { deleteProvider(provider) }
        )
    }

    /** ":" 弹窗：编辑 / 删除（位置逻辑仿首页历史记录，锚点坐标须在列表解散前捕获）。提示词与供应商共用。 */
    private fun showActionsPopup(
        anchorView: View,
        anchorLocation: IntArray,
        option1Text: String,
        option2Text: String,
        onOption1: () -> Unit,
        onOption2: () -> Unit
    ) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val popupWidth = (screenWidth * 0.30f).toInt()
        val popupHeight = (screenHeight * 0.25f).toInt().coerceAtLeast(120)

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_actions, null)
        popupView.layoutParams = LinearLayout.LayoutParams(popupWidth, popupHeight)

        val btnOption1 = popupView.findViewById<Button>(R.id.btnOption1)
        val btnOption2 = popupView.findViewById<Button>(R.id.btnOption2)
        btnOption1.text = option1Text
        btnOption2.text = option2Text

        val btnHeight = (popupHeight * 0.42f).toInt()
        val btnWidth = (popupWidth * 0.85f).toInt()
        val btnMargin = (popupHeight * 0.04f).toInt()
        btnOption1.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, btnMargin)
        }
        btnOption1.textSize = (popupWidth * 0.08f).coerceIn(10f, 20f)
        btnOption2.layoutParams = LinearLayout.LayoutParams(btnWidth, btnHeight).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        btnOption2.textSize = (popupWidth * 0.08f).coerceIn(10f, 20f)

        val actionsPopup = PopupWindow(popupView, popupWidth, popupHeight, true)
        actionsPopup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        actionsPopup.animationStyle = android.R.style.Animation_Dialog

        // 用仍在屏幕上的选择器作为锚点（拿 window token），位置按捕获的 ":" 坐标计算
        var x = anchorLocation[0] - popupWidth
        var y = anchorLocation[1] - popupHeight / 4
        if (y + popupHeight > screenHeight) y = anchorLocation[1] - popupHeight
        if (x < 0) x = 10
        if (x + popupWidth > screenWidth) x = screenWidth - popupWidth - 10
        actionsPopup.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)

        btnOption1.setOnClickListener {
            actionsPopup.dismiss()
            onOption1()
        }
        btnOption2.setOnClickListener {
            actionsPopup.dismiss()
            onOption2()
        }
        popupView.setOnTouchListener { _, _ ->
            actionsPopup.dismiss()
            false
        }
    }

    private fun deletePrompt(prompt: Prompt) {
        lifecycleScope.launch {
            val wasSelected = initialPromptId == prompt.id
            promptRepository.deletePrompt(prompt.id)
            if (wasSelected) {
                initialPromptId = PromptRepository.DEFAULT_PROMPT_ID
                listener?.onPromptSelected(PromptRepository.DEFAULT_PROMPT_ID)
            }
            loadPrompts()
            Toast.makeText(requireContext(), "已删除提示词：${prompt.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPromptDialog(edit: Prompt?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_prompt, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPromptDialogTitle)
        val etName = dialogView.findViewById<EditText>(R.id.etPromptName)
        val etContent = dialogView.findViewById<EditText>(R.id.etPromptContent)

        if (edit != null) {
            tvTitle.text = "编辑提示词"
            etName.setText(edit.name)
            etContent.setText(edit.content)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val name = etName.text.toString().trim()
            val content = etContent.text.toString().trim()
            if (name.isEmpty() || content.isEmpty()) {
                Toast.makeText(requireContext(), "名称和内容都不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            if (edit != null) {
                updatePrompt(edit.copy(name = name, content = content))
            } else {
                addPrompt(Prompt(name = name, content = content))
            }
        }

        dialog.show()
        clampDialogHeight(dialog, dialogView)
    }

    private fun addPrompt(prompt: Prompt) {
        lifecycleScope.launch {
            promptRepository.addPrompt(prompt)
            initialPromptId = prompt.id
            loadPrompts()
            listener?.onPromptSelected(prompt.id)
            Toast.makeText(requireContext(), "已添加提示词：${prompt.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePrompt(prompt: Prompt) {
        lifecycleScope.launch {
            promptRepository.updatePrompt(prompt)
            initialPromptId = prompt.id
            loadPrompts()
            listener?.onPromptSelected(prompt.id)
            Toast.makeText(requireContext(), "已更新提示词：${prompt.name}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_PROVIDER_ID = "arg_provider_id"
        private const val ARG_MODEL_ONLINE = "arg_model_online"
        private const val ARG_PROMPT_ID = "arg_prompt_id"
        private const val ARG_ALLOW_MANAGEMENT = "arg_allow_management"
        private const val ARG_CONTEXT_MODE = "arg_context_mode"
        private const val ARG_STREAM_ENABLED = "arg_stream_enabled"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SHOW_MODEL_ONLINE = "arg_show_model_online"
        private const val ARG_SHOW_STREAM_ENABLED = "arg_show_stream_enabled"
        private const val ARG_SHOW_CONTEXT_MODE = "arg_show_context_mode"
        private const val ARG_SHOW_PROMPT = "arg_show_prompt"
        private const val ARG_SHOW_PROVIDER = "arg_show_provider"

        fun newInstance(
            providerId: String?,
            modelOnline: Boolean,
            promptId: String?,
            allowManagement: Boolean,
            contextMode: String = ProviderRepository.CONTEXT_MODE_ALL,
            streamEnabled: Boolean = true,
            title: String = "AI 设置",
            showModelOnline: Boolean = true,
            showStreamEnabled: Boolean = true,
            showContextMode: Boolean = true,
            showPrompt: Boolean = true,
            showProvider: Boolean = true
        ): AiSettingsFragment {
            return AiSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER_ID, providerId)
                    putBoolean(ARG_MODEL_ONLINE, modelOnline)
                    putString(ARG_PROMPT_ID, promptId)
                    putBoolean(ARG_ALLOW_MANAGEMENT, allowManagement)
                    putString(ARG_CONTEXT_MODE, contextMode)
                    putBoolean(ARG_STREAM_ENABLED, streamEnabled)
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_SHOW_MODEL_ONLINE, showModelOnline)
                    putBoolean(ARG_SHOW_STREAM_ENABLED, showStreamEnabled)
                    putBoolean(ARG_SHOW_CONTEXT_MODE, showContextMode)
                    putBoolean(ARG_SHOW_PROMPT, showPrompt)
                    putBoolean(ARG_SHOW_PROVIDER, showProvider)
                }
            }
        }
    }
}
