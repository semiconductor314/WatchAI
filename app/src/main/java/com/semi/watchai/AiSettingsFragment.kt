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
    }

    var listener: Listener? = null

    // 两个宿主 Activity 都实现了 Listener，直接在此绑定，保证设置能持久化
    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    private lateinit var providerRepository: ProviderRepository
    private lateinit var promptRepository: PromptRepository
    private lateinit var spinnerProvider: Spinner
    private lateinit var llPromptSelector: LinearLayout
    private lateinit var tvSelectedPrompt: TextView
    private lateinit var switchModelOnline: Switch

    private val providers = mutableListOf<AiProvider>()
    private val prompts = mutableListOf<Prompt>()
    private var restoringSelection = false

    private var initialProviderId: String? = null
    private var initialModelOnline = false
    private var initialPromptId: String? = null
    private var allowManagement = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ai_settings, container, false)

        providerRepository = ProviderRepository(requireContext())
        promptRepository = PromptRepository(requireContext())
        spinnerProvider = view.findViewById(R.id.spinnerProvider)
        llPromptSelector = view.findViewById(R.id.llPromptSelector)
        tvSelectedPrompt = view.findViewById(R.id.tvSelectedPrompt)
        switchModelOnline = view.findViewById(R.id.switchModelOnline)

        arguments?.let {
            initialProviderId = it.getString(ARG_PROVIDER_ID)
            initialModelOnline = it.getBoolean(ARG_MODEL_ONLINE, false)
            initialPromptId = it.getString(ARG_PROMPT_ID)
            allowManagement = it.getBoolean(ARG_ALLOW_MANAGEMENT, true)
        }

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

        view.findViewById<Button>(R.id.btnAddProvider).setOnClickListener {
            showAddProviderDialog()
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

        spinnerProvider.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (restoringSelection) return
                    if (position in providers.indices) {
                        listener?.onProviderSelected(providers[position].id)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

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
            refreshProviderSpinner()
        }
    }

    private fun refreshProviderSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            providers.map { it.name }
        )
        adapter.setDropDownViewResource(R.layout.spinner_item)

        // 恢复选中时不触发保存逻辑（onItemSelected 同步回调）
        restoringSelection = true
        spinnerProvider.adapter = adapter
        val index = providers.indexOfFirst { it.id == initialProviderId }
        if (index >= 0) {
            spinnerProvider.setSelection(index)
        }
        restoringSelection = false
    }

    private fun showAddProviderDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_provider, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProviderName)
        val etUrl = dialogView.findViewById<EditText>(R.id.etProviderUrl)
        val etKey = dialogView.findViewById<EditText>(R.id.etProviderKey)

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
            addProvider(AiProvider(name = name, baseUrl = url, apiKey = key))
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

    /** ":" 弹窗：编辑 / 删除（位置逻辑仿首页历史记录，锚点坐标须在列表解散前捕获）。 */
    private fun showPromptActions(prompt: Prompt, anchorLocation: IntArray) {
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val popupWidth = (screenWidth * 0.30f).toInt()
        val popupHeight = (screenHeight * 0.25f).toInt().coerceAtLeast(120)

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_actions, null)
        popupView.layoutParams = LinearLayout.LayoutParams(popupWidth, popupHeight)

        val btnOption1 = popupView.findViewById<Button>(R.id.btnOption1)
        val btnOption2 = popupView.findViewById<Button>(R.id.btnOption2)
        btnOption1.text = "编辑"
        btnOption2.text = "删除"

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
        actionsPopup.showAtLocation(llPromptSelector, Gravity.NO_GRAVITY, x, y)

        btnOption1.setOnClickListener {
            actionsPopup.dismiss()
            showPromptDialog(edit = prompt)
        }
        btnOption2.setOnClickListener {
            actionsPopup.dismiss()
            deletePrompt(prompt)
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

        fun newInstance(
            providerId: String?,
            modelOnline: Boolean,
            promptId: String?,
            allowManagement: Boolean
        ): AiSettingsFragment {
            return AiSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROVIDER_ID, providerId)
                    putBoolean(ARG_MODEL_ONLINE, modelOnline)
                    putString(ARG_PROMPT_ID, promptId)
                    putBoolean(ARG_ALLOW_MANAGEMENT, allowManagement)
                }
            }
        }
    }
}
