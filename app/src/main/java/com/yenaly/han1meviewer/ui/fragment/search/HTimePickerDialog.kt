package com.yenaly.han1meviewer.ui.fragment.search

import android.content.Context
import android.content.DialogInterface
import android.util.Log
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.materialswitch.MaterialSwitch
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.util.createAlertDialog
import com.yenaly.han1meviewer.util.showWithBlurEffect
import java.util.Calendar



class HTimePickerDialog(
    val context: Context,
    @StringRes private val titleRes: Int,
)
{
    enum class Mode {
        YMD, YM, Y
    }
    data class DateSelection(
        val year: Int? = null,
        val month: Int? = null,
        val day: Int? = null
    )

    private val coreView = View.inflate(context, R.layout.pop_up_hanime_time_picker, null)

    private val yearSwitch: MaterialSwitch = coreView.findViewById(R.id.year_switch)
    private val yearPicker: NumberPicker = coreView.findViewById(R.id.year)
    private val monthPicker: NumberPicker = coreView.findViewById(R.id.month)
    private val dayPicker: NumberPicker = coreView.findViewById(R.id.day)

    private val yearText: TextView = coreView.findViewById(R.id.year_text)
    private val monthText: TextView = coreView.findViewById(R.id.month_text)
    private val dayText: TextView = coreView.findViewById(R.id.day_text)

    private var onSave: ((DateSelection) -> Unit)? = null

    private var onReset: ((DateSelection) -> Unit)? = null

    private var onDismiss: DialogInterface.OnDismissListener? = null

    private var oldMode: Mode = Mode.YMD
    private var mode: Mode = Mode.YMD

    private val dialog = context.createAlertDialog {
        setTitle(titleRes)
        setPositiveButton(R.string.save) {_, _ ->
            var date = when (mode) {
                Mode.YMD -> DateSelection(
                    year = yearPicker.value,
                    month = monthPicker.value,
                    day = dayPicker.value
                )
                Mode.YM -> DateSelection(
                    year = yearPicker.value,
                    month = monthPicker.value
                )
                Mode.Y -> DateSelection(
                    year = yearPicker.value
                )
            }
            onSave?.invoke(date)
        }
        setNeutralButton(R.string.reset) { _, _ ->
            var date = DateSelection()
            onReset?.invoke(date)
        }
        setView(coreView)
    }

    init {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        yearPicker.minValue = 1990
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear
        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        dayPicker.minValue = 1
        dayPicker.maxValue = 31

        yearSwitch.setOnClickListener {
            if (yearSwitch.isChecked) {
                setTimePickerMode(Mode.Y)
            } else {
                setTimePickerMode(oldMode)
            }
        }
    }

    fun setMode(mode: Mode) {
        oldMode = mode
        setTimePickerMode(mode)
    }

    fun setDate(year: Int? = null, month: Int? = null, day: Int? = null) {
        if (year != null) yearPicker.value = year
        if (month != null) monthPicker.value = month
        if (day != null) dayPicker.value = day
    }

    private fun setTimePickerMode(mode: Mode) {
        this.mode = mode
        when (mode) {
            Mode.YMD -> {
                yearSwitch.isChecked = false

                yearPicker.isVisible = true
                monthPicker.isVisible = true
                dayPicker.isVisible = true

                yearText.isVisible = true
                monthText.isVisible = true
                dayText.isVisible = true
            }
            Mode.YM -> {
                yearSwitch.isChecked = false

                yearPicker.isVisible = true
                monthPicker.isVisible = true
                dayPicker.isVisible = false

                yearText.isVisible = true
                monthText.isVisible = true
                dayText.isVisible = false
            }
            Mode.Y -> {
                yearSwitch.isChecked = true

                yearPicker.isVisible = true
                monthPicker.isVisible = false
                dayPicker.isVisible = false

                yearText.isVisible = true
                monthText.isVisible = false
                dayText.isVisible = false
            }
        }
    }

    fun setYearRange(minYear: Int? = null, maxYear: Int? = null) {
        if (minYear != null) yearPicker.minValue = minYear
        if (maxYear != null) yearPicker.maxValue = maxYear
    }

    fun setOnSaveListener(action: (DateSelection) -> Unit) {
        onSave = action
    }

    fun setonResetListener(action: (DateSelection) -> Unit) {
        onReset = action
    }

    fun setOnDismissListener(action: DialogInterface.OnDismissListener?) {
        onDismiss = action
    }

    fun show() {
        dialog.showWithBlurEffect({
            onDismiss?.onDismiss(it)
        })
    }
}