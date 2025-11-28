import Foundation

protocol SettingsStore {
    func load() async -> UserSettings
    func save(_ settings: UserSettings) async
}

final class UserDefaultsSettingsStore: SettingsStore {
    private let defaults = UserDefaults.standard
    private let key = "userSettings"

    func load() async -> UserSettings {
        if let data = defaults.data(forKey: key), let decoded = try? JSONDecoder().decode(UserSettings.self, from: data) {
            return decoded
        }
        return UserSettings(
            locationMode: .device,
            sunriseConfig: SunAlarmConfig(enabled: true, eventType: .sunrise, offsetMinutes: 0),
            sunsetConfig: SunAlarmConfig(enabled: false, eventType: .sunset, offsetMinutes: 0),
            timeFormat24h: true,
            onboardingComplete: false
        )
    }

    func save(_ settings: UserSettings) async {
        if let encoded = try? JSONEncoder().encode(settings) {
            defaults.set(encoded, forKey: key)
        }
    }
}
