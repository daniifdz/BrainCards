package org.braincards.app.presentation.screens.studyStats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.braincards.app.domain.models.StudyStats
import org.braincards.app.domain.usecase.Stats.GetStudyStatsUseCase

class StudyStatsVM(
    getStudyStatsUseCase: GetStudyStatsUseCase
) : ViewModel() {

    val stats: StateFlow<StudyStats> = getStudyStatsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StudyStats(0, 0, 0, 0)
        )
}