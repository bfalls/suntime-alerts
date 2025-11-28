import Foundation

enum SunEventType: String, Codable, CaseIterable {
    case sunrise
    case sunset
}

struct SunEvent: Codable, Identifiable {
    let id = UUID()
    let date: Date
    let type: SunEventType
    let dateTime: Date
    let locationUsed: Coordinate
}

struct SunAlarmConfig: Codable {
    var enabled: Bool
    var eventType: SunEventType
    var offsetMinutes: Int
}

//enum LocationMode: Codable, Identifiable {
enum LocationMode: Codable, CaseIterable, Identifiable, Hashable {
    case device
    case fixed(latitude: Double, longitude: Double)

    // Manual implementation because we have associated values
    static var allCases: [LocationMode] {
        [
            .device,
            // The concrete lat/lon values here are just placeholders for the picker;
            // your view model will store the real coordinates.
            .fixed(latitude: 0, longitude: 0)
        ]
    }
    
    var id: String { description }

    var description: String {
        switch self {
        case .device: return "Device location"
        case .fixed(let lat, let lon): return String(format: "Fixed (%.2f, %.2f)", lat, lon)
        }
    }
}

struct UserSettings: Codable {
    var locationMode: LocationMode
    var sunriseConfig: SunAlarmConfig
    var sunsetConfig: SunAlarmConfig
    var timeFormat24h: Bool
    var onboardingComplete: Bool
}

struct Coordinate: Codable {
    let latitude: Double
    let longitude: Double
}
