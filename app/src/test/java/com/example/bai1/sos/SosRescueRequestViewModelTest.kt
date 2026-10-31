package com.example.bai1.sos

import com.example.bai1.sos.presentation.SosFormData
import com.example.bai1.presentation.sos.SosRescueRequestViewModel
import com.example.bai1.sos.presentation.SosUiState
import com.example.bai1.sos.provider.BatteryInfoProvider
import com.example.bai1.presentation.location.LocationProvider
import com.example.bai1.presentation.location.LocationResult
import com.example.bai1.presentation.location.LocationError
import com.example.bai1.sos.provider.FakeNetworkAvailabilityProvider
import com.example.bai1.sos.repository.InMemorySosRepository
import com.example.bai1.sos.transport.FakeInternetSosTransport
import com.example.bai1.sos.transport.FakeMeshSosTransport
import com.example.bai1.sos.transport.SosTransportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeLocationProviderForTest : LocationProvider {
    override fun getCurrentLocation(onLocationResult: (LocationResult) -> Unit) {
        onLocationResult(LocationResult.Success(10.0, 20.0, 15f, 1_700_000_000_000L))
    }
}

class FakeBatteryInfoProviderForTest : BatteryInfoProvider {
    override fun getBatteryPercent(): Int = 80
}

class UnavailableLocationProviderForTest : LocationProvider {
    override fun getCurrentLocation(onLocationResult: (LocationResult) -> Unit) {
        onLocationResult(LocationResult.Error(LocationError.UNAVAILABLE))
    }
}

class InvalidLocationProviderForTest : LocationProvider {
    override fun getCurrentLocation(onLocationResult: (LocationResult) -> Unit) {
        onLocationResult(LocationResult.Success(120.0, 20.0, 5f, 1_700_000_000_000L))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SosRescueRequestViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state is Editing and fetches location`() {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val viewModel = SosRescueRequestViewModel(
            repository = repository,
            locationProvider = FakeLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        val state = viewModel.uiState.value
        assertTrue("Expected Editing state, got \${state::class.simpleName}", state is SosUiState.Editing)

        val form = viewModel.formData.value
        assertEquals(10.0, form.latitude)
        assertEquals(20.0, form.longitude)
    }

    @Test
    fun `test submitRequest transitions to Submitted on success`() {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val viewModel = SosRescueRequestViewModel(
            repository = repository,
            locationProvider = FakeLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        viewModel.updateAdultsCount(2)
        viewModel.updateCondition("Đứt tay")
        viewModel.updateLocation(10.0, 20.0, null)
        
        viewModel.submitRequest()

        val state = viewModel.uiState.value
        assertTrue("Expected Submitted state, got \${state::class.simpleName}", state is SosUiState.Submitted)
    }

    @Test
    fun `test submitRequest fails validation when location is missing`() {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val internetTransport = FakeInternetSosTransport(delayMs = 0, shouldFail = false)
        val meshTransport = FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        val transportManager = SosTransportManager(networkProvider, internetTransport, meshTransport)
        val repository = InMemorySosRepository(transportManager)

        val viewModel = SosRescueRequestViewModel(
            repository = repository,
            locationProvider = FakeLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        viewModel.updateAdultsCount(2)
        viewModel.updateCondition("Sập nhà")
        // Force location missing
        viewModel.updateLocation(null, null, null)
        
        viewModel.submitRequest()

        val state = viewModel.uiState.value
        assertTrue("Expected Editing state, got \${state::class.simpleName}", state is SosUiState.Editing)
        val editingState = state as SosUiState.Editing
        assertEquals("Không lấy được vị trí hiện tại", editingState.validationError)
    }

    @Test
    fun `location error is represented in editing state`() {
        val viewModel = SosRescueRequestViewModel(
            repository = repository(),
            locationProvider = UnavailableLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        val state = viewModel.uiState.value as SosUiState.Editing
        assertTrue(state.location is com.example.bai1.sos.presentation.LocationUiState.Error)
    }

    @Test
    fun `invalid provider location is rejected`() {
        val viewModel = SosRescueRequestViewModel(
            repository = repository(),
            locationProvider = InvalidLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        assertTrue(viewModel.formData.value.latitude == null)
        assertTrue(viewModel.uiState.value is SosUiState.Editing)
    }

    @Test
    fun `photo uri is optional and timestamp plus battery are captured`() {
        val repository = repository()
        val viewModel = SosRescueRequestViewModel(
            repository = repository,
            locationProvider = FakeLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        viewModel.refreshLocation()

        viewModel.updateCondition("Bị thương nhẹ")
        viewModel.updatePhoto(null)
        val beforeSubmit = System.currentTimeMillis()
        viewModel.submitRequest()

        val submitted = kotlinx.coroutines.runBlocking { repository.getMySos().single() }
        assertEquals(null, submitted.photoUri)
        assertTrue(submitted.createdAt >= beforeSubmit)
        assertEquals(80, submitted.batteryPercent)

        val withoutPhotoRepository = repository()
        val withoutPhoto = SosRescueRequestViewModel(
            repository = withoutPhotoRepository,
            locationProvider = FakeLocationProviderForTest(),
            batteryProvider = FakeBatteryInfoProviderForTest()
        )
        withoutPhoto.refreshLocation()
        withoutPhoto.updateCondition("Mất liên lạc")
        withoutPhoto.submitRequest()
        assertTrue(kotlinx.coroutines.runBlocking { withoutPhotoRepository.getMySos().single().photoUri == null })
    }

    private fun repository(): InMemorySosRepository {
        val networkProvider = FakeNetworkAvailabilityProvider(online = true)
        val transportManager = SosTransportManager(
            networkProvider,
            FakeInternetSosTransport(delayMs = 0, shouldFail = false),
            FakeMeshSosTransport(delayMs = 0, shouldFail = false)
        )
        return InMemorySosRepository(transportManager)
    }
}
