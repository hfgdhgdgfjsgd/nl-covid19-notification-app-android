/*
 * Copyright (c) 2020 De Staat der Nederlanden, Ministerie van Volksgezondheid, Welzijn en Sport.
 *  Licensed under the EUROPEAN UNION PUBLIC LICENCE v. 1.2
 *
 *  SPDX-License-Identifier: EUPL-1.2
 */
package nl.rijksoverheid.en.status

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import nl.rijksoverheid.en.ExposureNotificationsRepository
import nl.rijksoverheid.en.R
import nl.rijksoverheid.en.enapi.StatusResult
import nl.rijksoverheid.en.onboarding.OnboardingRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class StatusViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val notificationsRepository: ExposureNotificationsRepository
) : ViewModel() {

    private val refreshStatus = MutableLiveData(Unit)

    val headerViewState = refreshStatus.switchMap {
        liveData {
            emit(notificationsRepository.getStatus())
        }.switchMap { status ->
            notificationsRepository.getLastExposureDate()
                .asLiveData(viewModelScope.coroutineContext)
                .map { date -> status to date }
        }.map { (status, date) -> createHeaderViewState(status, date) }
    }.startWith(HeaderViewState.Active)

    val errorViewState = refreshStatus.switchMap {
        liveData {
            emit(notificationsRepository.getStatus())
        }.switchMap { status ->
            notificationsRepository.getLastExposureDate()
                .asLiveData(viewModelScope.coroutineContext)
                .map { date -> status to date }
        }.map { (status, date) -> createErrorViewState(status, date) }
    }.startWith(ErrorViewState.None)

    val shouldShowErrorState = errorViewState.map { it !is ErrorViewState.None }

    fun hasCompletedOnboarding(): Boolean {
        return onboardingRepository.hasCompletedOnboarding()
    }

    fun refreshStatus() {
        refreshStatus.value = Unit
    }

    fun getDescription(context: Context, state: HeaderViewState): String = when (state) {
        HeaderViewState.Active -> context.getString(R.string.status_no_exposure_detected_description)
        is HeaderViewState.Exposed -> context.getString(
            R.string.status_exposure_detected_description,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(state.date)
        )
        HeaderViewState.Disabled -> context.getString(R.string.status_en_api_disabled_description)
    }

    fun getErrorText(context: Context, error: ErrorViewState): String? = when (error) {
        is ErrorViewState.ConsentRequired -> context.getString(R.string.status_error_consent_required)
        else -> null
    }

    private fun createHeaderViewState(status: StatusResult, date: LocalDate?): HeaderViewState =
        if (date != null) {
            HeaderViewState.Exposed(date)
        } else {
            when (status) {
                is StatusResult.Enabled -> HeaderViewState.Active
                else -> HeaderViewState.Disabled
            }
        }

    private fun createErrorViewState(status: StatusResult, date: LocalDate?): ErrorViewState =
        if (status != StatusResult.Enabled && date != null) {
            ErrorViewState.ConsentRequired
        } else {
            // handled in header view state
            ErrorViewState.None
        }

    fun onPrimaryActionClicked(state: HeaderViewState) {
        // (headerViewState as MutableLiveData).value = HeaderViewState.BluetoothDisabled
    }

    fun onSecondaryActionClicked(state: HeaderViewState) {
    }

    fun onErrorActionClicked(state: ErrorViewState) {
    }

    sealed class HeaderViewState(
        @DrawableRes val icon: Int,
        @StringRes val headline: Int,
        @StringRes val primaryAction: Int?,
        @StringRes val secondaryAction: Int?
    ) {
        object Active : HeaderViewState(
            R.drawable.ic_status_no_exposure,
            R.string.status_no_exposure_detected_headline,
            null,
            null
        )

        data class Exposed(val date: LocalDate) : HeaderViewState(
            R.drawable.ic_status_exposure,
            R.string.status_exposure_detected_headline,
            R.string.status_exposure_what_next,
            R.string.status_reset_exposure
        )

        object Disabled : HeaderViewState(
            R.drawable.ic_status_disabled,
            R.string.status_disabled_headline,
            R.string.status_en_api_disabled_enable,
            null
        )
    }

    sealed class ErrorViewState(@StringRes val actionLabel: Int?) {
        object None : ErrorViewState(null)
        object ConsentRequired : ErrorViewState(R.string.status_error_action_consent)
    }
}

private fun <T> LiveData<T>.startWith(value: T): LiveData<T> {
    val mediator = MediatorLiveData<T>().apply {
        this.value = value
    }
    mediator.addSource(this) {
        mediator.value = it
    }
    return mediator
}
