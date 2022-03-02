package org.dhis2.usescases.eventsWithoutRegistration.eventInitial

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import java.util.Date
import org.dhis2.commons.prefs.Preference
import org.dhis2.commons.prefs.PreferenceProvider
import org.dhis2.commons.schedulers.SchedulerProvider
import org.dhis2.data.schedulers.TrampolineSchedulerProvider
import org.dhis2.form.data.RulesUtilsProvider
import org.dhis2.form.model.FieldUiModelImpl
import org.dhis2.usescases.eventsWithoutRegistration.eventCapture.EventFieldMapper
import org.dhis2.utils.Result
import org.dhis2.utils.analytics.AnalyticsHelper
import org.dhis2.utils.analytics.matomo.MatomoAnalyticsController
import org.hisp.dhis.android.core.category.CategoryCombo
import org.hisp.dhis.android.core.category.CategoryOption
import org.hisp.dhis.android.core.category.CategoryOptionCombo
import org.hisp.dhis.android.core.common.FeatureType
import org.hisp.dhis.android.core.common.Geometry
import org.hisp.dhis.android.core.common.ValueType
import org.hisp.dhis.android.core.event.Event
import org.hisp.dhis.android.core.event.EventEditableStatus.Editable
import org.hisp.dhis.android.core.event.EventEditableStatus.NonEditable
import org.hisp.dhis.android.core.event.EventNonEditableReason
import org.hisp.dhis.android.core.event.EventNonEditableReason.BLOCKED_BY_COMPLETION
import org.hisp.dhis.android.core.event.EventNonEditableReason.NO_DATA_WRITE_ACCESS
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit
import org.hisp.dhis.android.core.program.Program
import org.hisp.dhis.android.core.program.ProgramStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventInitialPresenterTest {
    lateinit var presenter: EventInitialPresenter
    private val view: EventInitialContract.View = mock()
    private val rulesUtilsProvider: RulesUtilsProvider = mock()
    private val eventInitialRepository: EventInitialRepository = mock()
    private val schedulers: SchedulerProvider = TrampolineSchedulerProvider()
    private val preferences: PreferenceProvider = mock()
    private val analyticsHelper: AnalyticsHelper = mock()
    private val matomoAnalyticsController: MatomoAnalyticsController = mock()
    private val eventFieldMapper: EventFieldMapper = mock()

    @Before
    fun setUp() {
        presenter = EventInitialPresenter(
            view,
            rulesUtilsProvider,
            eventInitialRepository,
            schedulers,
            preferences,
            analyticsHelper,
            matomoAnalyticsController,
            eventFieldMapper
        )
    }

    @Test
    fun `Should initialize when creating a new event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        initMocks("uid", null, "stageUid", catCombo)

        presenter.init("uid", null, "orgUnit", "stageUid")
        verify(view).setAccessDataWrite(true)
        verify(view).setProgram(any())
        verify(view).setProgramStage(any())
    }

    @Test
    fun `Should initialize when opening an event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        initMocks("uid", "eventId", "stageUid", catCombo)

        presenter.init("uid", "eventId", "orgUnit", "stageUid")
        verify(view).setAccessDataWrite(true)
        verify(view).setProgram(any())
        verify(view).setProgramStage(any())
        verify(view).setEvent(any())
        verify(view).updatePercentage(any())
    }

    @Test
    fun `Should init previous org unit if exist and no one is selected`() {
        whenever(preferences.contains(Preference.CURRENT_ORG_UNIT)) doReturn true
        whenever(preferences.getString(Preference.CURRENT_ORG_UNIT, null)) doReturn "prevOrgUnit"
        assertTrue(presenter.getCurrentOrgUnit(null) == "prevOrgUnit")
    }

    @Test
    fun `Should init previous org unit if exist and one is selected`() {
        whenever(preferences.contains(Preference.CURRENT_ORG_UNIT)) doReturn true
        whenever(preferences.getString(Preference.CURRENT_ORG_UNIT, null)) doReturn "prevOrgUnit"
        assertTrue(presenter.getCurrentOrgUnit("orgUnit") == "prevOrgUnit")
    }

    @Test
    fun `Should init given org unit if previous does not exist`() {
        whenever(preferences.contains(Preference.CURRENT_ORG_UNIT)) doReturn false
        assertTrue(presenter.getCurrentOrgUnit("orgUnit") == "orgUnit")
    }

    @Test
    fun `Should init null org unit if previous does not exist`() {
        whenever(preferences.contains(Preference.CURRENT_ORG_UNIT)) doReturn false
        assertTrue(presenter.getCurrentOrgUnit(null).isNullOrEmpty())
    }

    @Test
    fun `Should show QR code when clicking on share button`() {
        presenter.onShareClick()
        verify(view).showQR()
    }

    @Test
    fun `Should delete event when the delete option is clicked`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        initMocks("uid", "event", "stageUid", catCombo)

        presenter.init("uid", "event", "orgUnit", "stage")
        presenter.deleteEvent("tei_uid")
        verify(eventInitialRepository).deleteEvent(any(), any())
        verify(view).showEventWasDeleted()
    }

    @Test
    fun `Should show message when event is not yet created and the delete option is clicked`() {
        whenever(view.context) doReturn mock()
        whenever(view.context.getString(any())) doReturn "display message"

        presenter.deleteEvent("tei_uid")
        verify(view).displayMessage(any())
    }

    @Test
    fun `Should return true if enrollment is open`() {
        whenever(eventInitialRepository.isEnrollmentOpen) doReturn true
        assertTrue(presenter.isEnrollmentOpen)
    }

    @Test
    fun `Should return false if enrollment is not open`() {
        whenever(eventInitialRepository.isEnrollmentOpen) doReturn false
        assertFalse(presenter.isEnrollmentOpen)
    }

    @Test
    fun `Should get programStage`() {
        val programStage = ProgramStage.builder().uid("stage").build()
        whenever(
            eventInitialRepository.programStageWithId("stage")
        ) doReturn Observable.just(programStage)

        presenter.getProgramStage("stage")
        verify(view).setProgramStage(programStage)
    }

    @Test
    fun `Should go back on closing a previously saved event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        initMocks("uid", "event", "stageUid", catCombo)

        presenter.init("uid", "event", "orgUnit", "stage")
        presenter.onBackClick()
        verify(preferences).removeValue(Preference.EVENT_COORDINATE_CHANGED)
        verify(analyticsHelper).setEvent(any(), any(), any())
        verify(view).back()
    }

    @Test
    fun `Should go back without analytics when new event`() {
        presenter.onBackClick()
        verify(preferences).removeValue(Preference.EVENT_COORDINATE_CHANGED)
        verify(view).back()
    }

    @Test
    fun `Should create an event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.createEvent(
                "enrollment",
                "tei",
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.just("event")

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.createEvent(
            "enrollment", "stage", date, "orgUnit", "catCombo", "catOption", geometry, "tei"
        )

        verify(view).onEventCreated("event")
    }

    @Test
    fun `Should show error when there is an error creating an event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.createEvent(
                "enrollment",
                "tei",
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.error(Throwable("Error"))

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.createEvent(
            "enrollment", "stage", date, "orgUnit", "catCombo", "catOption", geometry, "tei"
        )

        verify(view).renderError("Error")
    }

    @Test
    fun `Should create an schedule event permanent`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.scheduleEvent(
                "enrollment",
                null,
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.just("event")

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.scheduleEventPermanent(
            "enrollment", null, "stage", date, "orgUnit", "catCombo", "catOption", geometry
        )

        verify(view).onEventCreated("event")
    }

    @Test
    fun `Should how error when there is an problem creating a scheduled event permanent`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.scheduleEvent(
                "enrollment",
                null,
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.error(Throwable("Error"))

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.scheduleEventPermanent(
            "enrollment", null, "stage", date, "orgUnit", "catCombo", "catOption", geometry
        )

        verify(view).renderError("Error")
    }

    @Test
    fun `Should create an schedule event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.scheduleEvent(
                "enrollment",
                null,
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.just("event")

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.scheduleEvent(
            "enrollment", "stage", date, "orgUnit", "catCombo", "catOption", geometry
        )

        verify(view).onEventCreated("event")
    }

    @Test
    fun `Should how error when there is an problem creating a scheduled event`() {
        val catCombo = CategoryCombo.builder().uid("catCombo").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        val date = Date()
        initMocks("uid", null, "stage", catCombo)
        whenever(
            eventInitialRepository.scheduleEvent(
                "enrollment",
                null,
                "uid",
                "stage",
                date,
                "orgUnit",
                "catCombo",
                "catOption",
                geometry
            )
        ) doReturn Observable.error(Throwable("Error"))

        presenter.init("uid", null, "orgUnit", "stage")
        presenter.scheduleEvent(
            "enrollment", "stage", date, "orgUnit", "catCombo", "catOption", geometry
        )

        verify(view).renderError("Error")
    }

    @Test
    fun `Should successfully edit event`() {
        val event = Event.builder().uid("event_uid").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        whenever(
            eventInitialRepository.editableStatus
        ) doReturn Flowable.just(Editable())
        whenever(
            eventInitialRepository.editEvent(
                "tei", "event_uid", "date", "orgUnit", "catCombo", "catOptionCombo", geometry
            )
        ) doReturn Observable.just(event)

        presenter.editEvent(
            "tei",
            "stage",
            "event_uid",
            "date",
            "orgUnit",
            "catCombo",
            "catOptionCombo",
            geometry
        )

        verify(view).onEventUpdated("event_uid")
    }

    @Test
    fun `Should not update event status on ReadOnly event`() {
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        whenever(
            eventInitialRepository.editableStatus
        ) doReturn Flowable.just(NonEditable(NO_DATA_WRITE_ACCESS))

        presenter.editEvent(
            "tei",
            "stage",
            "event_uid",
            "date",
            "orgUnit",
            "catCombo",
            "catOptionCombo",
            geometry
        )

        verify(view).onEventUpdated("event_uid")
    }

    @Test
    fun `Should display message when there is a problem editing event`() {
        val event = Event.builder().uid("event_uid").build()
        val geometry = Geometry.builder().type(FeatureType.POINT).build()
        whenever(
            eventInitialRepository.editableStatus
        ) doReturn Flowable.just(Editable())
        whenever(
            eventInitialRepository.editEvent(
                "tei", "event_uid", "date", "orgUnit", "catCombo", "catOptionCombo", geometry
            )
        ) doReturn Observable.error(Throwable("Error"))

        presenter.editEvent(
            "tei",
            "stage",
            "event_uid",
            "date",
            "orgUnit",
            "catCombo",
            "catOptionCombo",
            geometry
        )

        verify(view).displayMessage("Error")
    }

    @Test
    fun `Should clear disposable`() {
        val size = presenter.compositeDisposable.size()
        presenter.onDettach()
        assert(size == 0)
    }

    @Test
    fun `Should set changing coordinates`() {
        presenter.setChangingCoordinates(true)
        verify(preferences).setValue(Preference.EVENT_COORDINATE_CHANGED, true)
    }

    @Test
    fun `Should clear changing coordinates`() {
        presenter.setChangingCoordinates(false)
        verify(preferences).removeValue(Preference.EVENT_COORDINATE_CHANGED)
    }

    @Test
    fun `Should not show the completion percentage view`() {
        val shouldShow = false
        whenever(eventInitialRepository.showCompletionPercentage()) doReturn shouldShow

        val result = presenter.completionPercentageVisibility
        assert(shouldShow == result)
    }

    @Test
    fun `Should show the completion percentage view`() {
        val shouldShow = true
        whenever(eventInitialRepository.showCompletionPercentage()) doReturn shouldShow

        val result = presenter.completionPercentageVisibility
        assert(shouldShow == result)
    }

    @Test
    fun `Should track event analytics`() {
        presenter.onEventCreated()

        verify(matomoAnalyticsController).trackEvent(any(), any(), any())
    }

    @Test
    fun `Should return true if event is editable`() {
        whenever(
            eventInitialRepository.editableStatus
        ) doReturn Flowable.just(Editable())

        val isEditable = presenter.isEventEditable
        assert(isEditable)
    }

    @Test
    fun `Should return false if event is not editable`() {
        whenever(
            eventInitialRepository.editableStatus
        ) doReturn Flowable.just(NonEditable(BLOCKED_BY_COMPLETION))

        val isEditable = presenter.isEventEditable
        assert(!isEditable)
    }

    private fun initMocks(
        uid: String?,
        eventId: String?,
        programStageUid: String?,
        catCombo: CategoryCombo,
        moreOrgUnits: Boolean = false
    ) {
        val program = Program.builder().uid(uid).build()
        val orgUnits =
            mutableListOf(OrganisationUnit.builder().uid("orgUnit").displayName("name").build())
        val programStage = ProgramStage.builder().uid(programStageUid).build()

        if (moreOrgUnits) {
            orgUnits.add(OrganisationUnit.builder().uid("orgUnit2").displayName("name").build())
        }

        whenever(eventInitialRepository.accessDataWrite(uid)) doReturn Observable.just(true)
        whenever(eventInitialRepository.getGeometryModel(uid)) doReturn Single.just(
            FieldUiModelImpl(
                uid = "id",
                layoutId = 1,
                value = "",
                focused = false,
                error = null,
                editable = true,
                warning = null,
                mandatory = false,
                label = "",
                programStageSection = null,
                style = null,
                hint = "",
                description = null,
                valueType = ValueType.COORDINATE,
                legend = null,
                optionSet = null,
                allowFutureDates = null
            )
        )
        whenever(eventInitialRepository.getProgramWithId(uid)) doReturn Observable.just(program)
        whenever(eventInitialRepository.catCombo(uid)) doReturn Observable.just(catCombo)
        whenever(eventInitialRepository.orgUnits(uid)) doReturn Observable.just(orgUnits.toList())
        whenever(
            eventInitialRepository.catOptionCombos(catCombo.uid())
        ) doReturn Observable.just(listOf<CategoryOptionCombo>())
        whenever(
            eventInitialRepository.getOrganisationUnit("orgUnit")
        ) doReturn Observable.just(orgUnits[0])

        if (eventId != null) {
            eventMocks(eventId)
        } else {
            whenever(
                eventInitialRepository.programStageWithId(programStageUid)
            ) doReturn Observable.just(programStage)
        }
    }

    private fun eventMocks(eventId: String) {
        val event = Event.builder().uid(eventId).build()
        val programStage = ProgramStage.builder().uid(eventId).build()
        val stringCategoryOptionMap = mutableMapOf<String, CategoryOption>()
        val editionStatus = NonEditable(EventNonEditableReason.BLOCKED_BY_COMPLETION)

        whenever(eventInitialRepository.event(eventId)) doReturn Observable.just(event)
        whenever(
            eventInitialRepository.programStageForEvent(eventId)
        ) doReturn Flowable.just(programStage)
        whenever(
            eventInitialRepository.getOptionsFromCatOptionCombo(eventId)
        ) doReturn Flowable.just(stringCategoryOptionMap)

        whenever(eventInitialRepository.list()) doReturn Flowable.just(listOf())
        whenever(
            eventInitialRepository.calculate()
        ) doReturn Flowable.just(Result.success(listOf()))
        whenever(eventInitialRepository.eventSections()) doReturn Flowable.just(listOf())
        whenever(
            eventFieldMapper.map(
                mutableListOf(),
                mutableListOf(),
                "",
                mutableMapOf(),
                mutableMapOf(),
                mutableMapOf(),
                false to false
            )
        ) doReturn Pair(mutableListOf(), mutableListOf())
        whenever(eventInitialRepository.editableStatus) doReturn Flowable.just(editionStatus)
        whenever(view.context) doReturn mock()
        whenever(view.context.getString(any())) doReturn "reason"
    }
}
