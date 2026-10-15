package de.lionsun.plantags.dialogs

import androidx.appcompat.app.AlertDialog
import de.lionsun.plantags.R
import de.lionsun.plantags.activities.SimpleActivity
import de.lionsun.plantags.databinding.DialogEditRepeatingEventBinding
import de.lionsun.plantags.helpers.EDIT_ALL_OCCURRENCES
import de.lionsun.plantags.helpers.EDIT_FUTURE_OCCURRENCES
import de.lionsun.plantags.helpers.EDIT_SELECTED_OCCURRENCE
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding

class EditRepeatingEventDialog(val activity: SimpleActivity, val isTask: Boolean = false, val callback: (allOccurrences: Int?) -> Unit) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogEditRepeatingEventBinding::inflate)

    init {
        binding.apply {
            editRepeatingEventOneOnly.setOnClickListener { sendResult(EDIT_SELECTED_OCCURRENCE) }
            editRepeatingEventThisAndFutureOccurences.setOnClickListener { sendResult(EDIT_FUTURE_OCCURRENCES) }
            editRepeatingEventAllOccurrences.setOnClickListener { sendResult(EDIT_ALL_OCCURRENCES) }

            if (isTask) {
                editRepeatingEventTitle.setText(R.string.task_is_repeatable)
            } else {
                editRepeatingEventTitle.setText(R.string.event_is_repeatable)
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.hideKeyboard()
                    alertDialog.setOnDismissListener { sendResult(null) }
                }
            }
    }

    private fun sendResult(allOccurrences: Int?) {
        callback(allOccurrences)
        if (allOccurrences != null) {
            dialog?.dismiss()
        }
    }
}
