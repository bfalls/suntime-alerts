import Foundation

@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var locationMode: LocationMode = .device
    @Published var timeFormat24h: Bool = true

    private let settingsStore: SettingsStore

    init(settingsStore: SettingsStore) {
        self.settingsStore = settingsStore
        Task { await load() }
    }

    func load() async {
        let settings = await settingsStore.load()
        locationMode = settings.locationMode
        timeFormat24h = settings.timeFormat24h
    }

    func save() async {
        var settings = await settingsStore.load()
        settings.locationMode = locationMode
        settings.timeFormat24h = timeFormat24h
        await settingsStore.save(settings)
    }
}
