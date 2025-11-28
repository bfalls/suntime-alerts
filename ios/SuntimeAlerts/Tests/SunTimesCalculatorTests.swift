import XCTest
@testable import SuntimeAlerts

final class SunTimesCalculatorTests: XCTestCase {
    func testSunriseSunsetNewYorkSolstice() throws {
        let calculator = SunTimesCalculator()
        let coordinate = Coordinate(latitude: 40.7128, longitude: -74.0060)

        var comps = DateComponents()
        comps.year = 2023
        comps.month = 6
        comps.day = 21
        comps.timeZone = TimeZone(identifier: "America/New_York")
        let calendar = Calendar(identifier: .gregorian)
        let date = calendar.date(from: comps)!

        let times = calculator.calculateSunTimes(for: date, coordinate: coordinate, timeZone: comps.timeZone!)
        guard let sunrise = times.sunrise, let sunset = times.sunset else {
            XCTFail("Expected valid sunrise and sunset")
            return
        }

        let formatter = DateFormatter()
        formatter.timeZone = comps.timeZone
        formatter.dateFormat = "HH:mm"
        let sunriseString = formatter.string(from: sunrise)
        let sunsetString = formatter.string(from: sunset)

        XCTAssertEqual(sunriseString, "05:24", "Approximate sunrise in NYC on summer solstice", accuracy: 5)
        XCTAssertEqual(sunsetString, "20:30", "Approximate sunset in NYC on summer solstice", accuracy: 5)
    }
}

private extension XCTestCase {
    func XCTAssertEqual(_ lhs: String, _ rhs: String, _ message: String, accuracy minutes: Int, file: StaticString = #file, line: UInt = #line) {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        guard let lhsDate = formatter.date(from: lhs), let rhsDate = formatter.date(from: rhs) else {
            XCTFail("Could not parse times", file: file, line: line)
            return
        }
        let delta = abs(lhsDate.timeIntervalSinceReferenceDate - rhsDate.timeIntervalSinceReferenceDate)
        XCTAssertLessThanOrEqual(delta, Double(minutes * 60), message, file: file, line: line)
    }
}
