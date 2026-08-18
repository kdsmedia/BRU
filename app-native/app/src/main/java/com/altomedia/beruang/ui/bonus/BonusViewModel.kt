package com.altomedia.beruang.ui.bonus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altomedia.beruang.data.AppConstants
import com.altomedia.beruang.data.BonusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks the per-day Bonus-task progress (checkin, ad-watch, comments, posts,
 * add-friend) so the [BonusScreen] can show live progress and rewards.
 */
class BonusViewModel : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val checkin: Boolean = false,
        val ads: Int = 0,
        val comments: Int = 0,
        val posts: Int = 0,
        val friends: Int = 0,
        val invitedCount: Int = 0,
        val inviteRewardClaimed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = BonusRepository.load(uid)
            val invited = BonusRepository.loadInviteCount(uid)
            val claimed = BonusRepository.isInviteRewardClaimed(uid)
            _state.value = State(
                loading = false,
                checkin = s.checkin,
                ads = s.ads,
                comments = s.comments,
                posts = s.posts,
                friends = s.friends,
                invitedCount = invited,
                inviteRewardClaimed = claimed,
            )
        }
    }

    fun refresh(uid: String) = start(uid)

    fun doCheckin(uid: String, onResult: (Boolean, Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = BonusRepository.doCheckin(uid)
            if (ok) {
                refresh(uid)
                onResult(true, AppConstants.Bonus.POINTS_CHECKIN)
            } else {
                onResult(false, 0)
            }
        }
    }

    fun watchAd(uid: String, onResult: (Boolean, Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = BonusRepository.recordAdWatch(uid)
            if (ok) {
                refresh(uid)
                onResult(true, AppConstants.Bonus.POINTS_AD_VALID)
            } else {
                onResult(false, 0)
            }
        }
    }

    /** Claim the one-time invite-10 milestone reward (5000 pts). */
    fun claimInvite(uid: String, onResult: (Boolean, Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = BonusRepository.claimInviteReward(uid)
            refresh(uid)
            onResult(ok, if (ok) AppConstants.INVITE_REWARD else 0L)
        }
    }
}
