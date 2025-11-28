import SwiftUI

@main
struct SuntimeAlertsApp: App {
    @StateObject private var container = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(container.settingsViewModel)
                .environmentObject(container.homeViewModel)
        }
    }
}

final class AppContainer: ObservableObject {
    let settingsStore: SettingsStore
    let locationService: LocationService
    let sunTimesCalculator: SunTimesCalculator
    let scheduleService: SunScheduleService
    let notificationScheduler: NotificationScheduler
    let homeViewModel: HomeViewModel
    let settingsViewModel: SettingsViewModel

    init() {
        self.settingsStore = UserDefaultsSettingsStore()
        self.locationService = CoreLocationService()
        self.sunTimesCalculator = SunTimesCalculator()
        self.notificationScheduler = NotificationScheduler()
        self.scheduleService = SunScheduleService(calculator: sunTimesCalculator, settingsStore: settingsStore, notificationScheduler: notificationScheduler)
        self.homeViewModel = HomeViewModel(scheduleService: scheduleService, locationService: locationService, settingsStore: settingsStore)
        self.settingsViewModel = SettingsViewModel(settingsStore: settingsStore)
    }
}
