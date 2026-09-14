package com.nendo.argosy.ui.screens.gamedetail.comments

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class GameCommentsInputHandler(private val viewModel: GameCommentsViewModel, private val back: () -> Unit) : InputHandler {
    override fun onUp() = InputResult.HANDLED.also { viewModel.moveVertical(-1) }
    override fun onDown() = InputResult.HANDLED.also { viewModel.moveVertical(1) }
    override fun onLeft() = InputResult.HANDLED.also { viewModel.moveHorizontal(-1) }
    override fun onRight() = InputResult.HANDLED.also { viewModel.moveHorizontal(1) }
    override fun onConfirm() = InputResult.HANDLED.also { viewModel.activate() }
    override fun onBack() = InputResult.HANDLED.also { back() }
    override fun onPrevTrigger() = InputResult.HANDLED.also { viewModel.scrollPage(-1) }
    override fun onNextTrigger() = InputResult.HANDLED.also { viewModel.scrollPage(1) }
    override fun onMenu() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onContextMenu() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
}
