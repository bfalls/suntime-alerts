import Foundation

final class SunScheduleService {
    private let calculator: SunTimesCalculator
    private let settingsStore: SettingsStore
    private let notificationScheduler: NotificationScheduler

    init(calculator: SunTimesCalculator, settingsStore: SettingsStore, notificationScheduler: NotificationScheduler) {
        self.calculator = calculator
        self.settingsStore = settingsStore
        self.notificationScheduler = notificationScheduler
    }

    func computeEvents(for date: Date, coordinate: Coordinate, timeZone: TimeZone = .current) -> [SunEvent] {
        let times = calculator.calculateSunTimes(for: date, coordinate: coordinate, timeZone: timeZone)
        var events: [SunEvent] = []
        if let sunrise = times.sunrise {
            events.append(SunEvent(date: date, type: .sunrise, dateTime: sunrise, locationUsed: coordinate))
        }
        if let sunset = times.sunset {
            events.append(SunEvent(date: date, type: .sunset, dateTime: sunset, locationUsed: coordinate))
        }
        return events
    }

    func scheduleAlarms(for coordinate: Coordinate) async {
        let settings = await settingsStore.load()
        let today = Date()
        let timeZone = TimeZone.current
        let dates = [today, Calendar.current.date(byAdding: .day, value: 1, to: today)!]
        notificationScheduler.cancelAll()

        for date in dates {
            let events = computeEvents(for: date, coordinate: coordinate, timeZone: timeZone)
            for event in events {
                let config = event.type == .sunrise ? settings.sunriseConfig : settings.sunsetConfig
                guard config.enabled else { continue }
                if let triggerDate = Calendar.current.date(byAdding: .minute, value: config.offsetMinutes, to: event.dateTime) {
                    notificationScheduler.scheduleNotification(for: event, at: triggerDate)
                }
            }
        }
    }
}
