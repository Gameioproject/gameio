package com.nendo.argosy.ui.screens.gamedetail.comments

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Composable
internal fun CommentCard(entry: CommentsEntry.Comment, state: GameCommentsState, focused: Boolean, viewModel: GameCommentsViewModel, onClick: () -> Unit) {
    val comment = entry.comment
    LaunchedEffect(comment.author?.id, state.userId) { viewModel.loadAvatar(comment.author) }
    val relativeTime = remember(comment.createdAt) {
        val time = runCatching { Instant.parse(comment.createdAt) }.getOrElse {
            runCatching { LocalDateTime.parse(comment.createdAt).toInstant(ZoneOffset.UTC) }.getOrNull()
        }
        time?.let { DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }.orEmpty()
    }
    Column(Modifier.fillMaxWidth().padding(start = if (entry.reply) Dimens.spacingXl else Dimens.spacingXs)
        .clip(RoundedCornerShape(Dimens.radiusMd))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .argosyFocusIndicators(focused, FocusIndicators.NavRow)
        .clickableNoFocus(onClick).padding(Dimens.spacingMd), verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            Box(Modifier.size(Dimens.avatarSm).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(comment.author?.username?.take(1)?.uppercase().orEmpty(), color = MaterialTheme.colorScheme.onPrimaryContainer)
                state.avatars[comment.author?.id]?.let { bytes ->
                    AsyncImage(bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(comment.author?.username ?: stringResource(R.string.comments_deleted_user), style = MaterialTheme.typography.labelLarge)
            Text(relativeTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (comment.edited) Text(stringResource(R.string.comments_edited), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val hidden = comment.spoiler && comment.id !in state.revealedSpoilers
        Text(when {
            comment.deleted -> stringResource(R.string.comments_deleted)
            hidden -> stringResource(R.string.comments_spoiler_hidden)
            else -> comment.body
        }, style = MaterialTheme.typography.bodyLarge, color = if (comment.deleted || hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (!comment.deleted) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
                Text("${if (comment.liked) "♥" else "♡"} ${comment.likeCount}", color = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickableNoFocus { viewModel.like(comment) }.padding(Dimens.spacingSm))
                if (!entry.rootDeleted) Text(stringResource(R.string.comments_reply), style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickableNoFocus {
                    viewModel.openActions(comment, entry.rootDeleted); viewModel.chooseAction(CommentAction.REPLY)
                }.padding(Dimens.spacingSm))
                Text(stringResource(R.string.comments_options), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
