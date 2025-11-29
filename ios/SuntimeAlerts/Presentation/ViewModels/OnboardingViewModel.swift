import Foundation

@MainActor
final class OnboardingViewModel: ObservableObject {
    enum Step: Int, CaseIterable {
        case welcome
        case location
        case notifications
        case alarms
        case summary

        var title: String {
            switch self {
            case .welcome: return "Welcome"
            case .location: return "Location"
            case .notifications: return "Notifications"
            case .alarms: return "Alarms"
            case .summary: return "Summary"
            }
        }
    }

    @Published var step: Step = .welcome
    @Published var onboardingComplete: Bool = false
    @Published var isLoaded: Bool = false
    @Published var locationMode: LocationMode = .device
    @Published var fixedLatitude: String = "0.0"
    @Published var fixedLongitude: String = "0.0"
    @Published var notificationsEnabled: Bool = true
    @Published var sunriseEnabled: Bool = true
    @Published var sunriseOffsetMinutes: Int = 0
    @Published var sunsetEnabled: Bool = false
    @Published var sunsetOffsetMinutes: Int = 0

    private let settingsStore: SettingsStore

    init(settingsStore: SettingsStore) {
        self.settingsStore = settingsStore
        Task { await load() }
    }

    func load() async {
        let settings = await settingsStore.load()
        onboardingComplete = settings.onboardingComplete
        locationMode = settings.locationMode
        if case let .fixed(latitude, longitude) = settings.locationMode {
            fixedLatitude = String(format: "%.4f", latitude)
            fixedLongitude = String(format: "%.4f", longitude)
        }
        sunriseEnabled = settings.sunriseConfig.enabled
        sunriseOffsetMinutes = settings.sunriseConfig.offsetMinutes
        sunsetEnabled = settings.sunsetConfig.enabled
        sunsetOffsetMinutes = settings.sunsetConfig.offsetMinutes
        isLoaded = true
    }

    func next() {
        guard let nextStep = Step(rawValue: step.rawValue + 1) else { return }
        step = nextStep
    }

    func back() {
        guard let prevStep = Step(rawValue: step.rawValue - 1) else { return }
        step = prevStep
    }

    var canAdvance: Bool {
        switch step {
        case .location:
            if case .fixed = locationMode { return Double(fixedLatitude) != nil && Double(fixedLongitude) != nil }
            return true
        default:
            return true
        }
    }

    func finish() async {
        var settings = await settingsStore.load()
        if case .fixed = locationMode, let lat = Double(fixedLatitude), let lon = Double(fixedLongitude) {
            settings.locationMode = .fixed(latitude: lat, longitude: lon)
        } else {
            settings.locationMode = .device
        }
        let sunriseConfig = SunAlarmConfig(enabled: notificationsEnabled ? sunriseEnabled : false, eventType: .sunrise, offsetMinutes: sunriseOffsetMinutes)
        let sunsetConfig = SunAlarmConfig(enabled: notificationsEnabled ? sunsetEnabled : false, eventType: .sunset, offsetMinutes: sunsetOffsetMinutes)
        settings.sunriseConfig = sunriseConfig
        settings.sunsetConfig = sunsetConfig
        settings.onboardingComplete = true
        onboardingComplete = true
        await settingsStore.save(settings)
    }
}
