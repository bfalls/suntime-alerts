import Foundation
import UserNotifications

final class NotificationScheduler {
    private let center = UNUserNotificationCenter.current()

    func requestAuthorization() async throws {
        try await center.requestAuthorization(options: [.alert, .sound])
    }

    func scheduleNotification(for event: SunEvent, at date: Date) {
        let content = UNMutableNotificationContent()
        content.title = event.type == .sunrise ? "Sunrise" : "Sunset"
        content.body = "Alarm for \(event.type == .sunrise ? "sunrise" : "sunset")"

        let triggerDate = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        let trigger = UNCalendarNotificationTrigger(dateMatching: triggerDate, repeats: false)
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
        center.add(request)
    }

    func cancelAll() {
        center.removeAllPendingNotificationRequests()
    }
}
