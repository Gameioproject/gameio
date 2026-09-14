package com.nendo.argosy.ui.screens.addons

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * Captures the setup parent as well as global drawer shortcuts while managing sources.
 */
class AddonsInputHandler(
    private val viewModel: AddonsViewModel,
    private val onExit: () -> Unit
) : InputHandler {
    override fun onUp(): InputResult { viewModel.move(-1); return InputResult.HANDLED }
    override fun onDown(): InputResult { viewModel.move(1); return InputResult.HANDLED }
    override fun onConfirm(): InputResult { viewModel.confirm(onExit); return InputResult.HANDLED }
    override fun onBack(): InputResult { viewModel.exit(onExit); return InputResult.HANDLED }
    override fun onLeft(): InputResult { viewModel.setFocusedEnabled(false); return InputResult.HANDLED }
    override fun onRight(): InputResult { viewModel.setFocusedEnabled(true); return InputResult.HANDLED }
    override fun onMenu() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onContextMenu() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onPrevTrigger() = InputResult.HANDLED
    override fun onNextTrigger() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
}
