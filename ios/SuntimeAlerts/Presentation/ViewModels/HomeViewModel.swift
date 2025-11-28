import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var todaySunriseString: String = "--"
    @Published var todaySunsetString: String = "--"
    @Published var sunriseConfig: SunAlarmConfig
    @Published var sunsetConfig: SunAlarmConfig

    private let scheduleService: SunScheduleService
    private let locationService: LocationService
    private let settingsStore: SettingsStore

    init(scheduleService: SunScheduleService, locationService: LocationService, settingsStore: SettingsStore) {
        self.scheduleService = scheduleService
        self.locationService = locationService
        self.settingsStore = settingsStore
        self.sunriseConfig = SunAlarmConfig(enabled: true, eventType: .sunrise, offsetMinutes: 0)
        self.sunsetConfig = SunAlarmConfig(enabled: false, eventType: .sunset, offsetMinutes: 0)
    }

    func refreshIfNeeded() async {
        let settings = await settingsStore.load()
        sunriseConfig = settings.sunriseConfig
        sunsetConfig = settings.sunsetConfig
        await updateSunTimes()
    }

    func rescheduleNow() async {
        await saveSettings()
        guard let coordinate = try? await locationService.currentCoordinate() else { return }
        await scheduleService.scheduleAlarms(for: coordinate)
        await updateSunTimes(coordinate: coordinate)
    }

    private func saveSettings() async {
        var settings = await settingsStore.load()
        settings.sunriseConfig = sunriseConfig
        settings.sunsetConfig = sunsetConfig
        await settingsStore.save(settings)
    }

    private func updateSunTimes(coordinate: Coordinate? = nil) async {
        let coord: Coordinate
        if let coordinate { coord = coordinate }
        else if let fetched = try? await locationService.currentCoordinate() { coord = fetched }
        else { return }
        let events = scheduleService.computeEvents(for: Date(), coordinate: coord)
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        if let sunrise = events.first(where: { $0.type == .sunrise })?.dateTime {
            todaySunriseString = formatter.string(from: sunrise)
        }
        if let sunset = events.first(where: { $0.type == .sunset })?.dateTime {
            todaySunsetString = formatter.string(from: sunset)
        }
    }
}
