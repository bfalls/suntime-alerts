import Foundation

/// NOAA-inspired solar calculation for sunrise/sunset. Pure Swift with no platform dependencies.
struct SunTimesCalculator {
    struct SunTimes {
        let sunrise: Date?
        let sunset: Date?
    }

    func calculateSunTimes(for targetDate: Date, coordinate: Coordinate, timeZone: TimeZone = .current) -> SunTimes {
        let calendar = Calendar(identifier: .gregorian)
        var components = calendar.dateComponents(in: timeZone, from: targetDate)
        components.hour = 0
        components.minute = 0
        components.second = 0
        guard let dayStart = calendar.date(from: components) else { return SunTimes(sunrise: nil, sunset: nil) }

        // Julian day calculation
        let julianDay = Self.julianDay(for: dayStart, timeZone: timeZone)
        let julianCentury = (julianDay - 2451545.0) / 36525.0

        func solarCoordinates(jc: Double) -> (declination: Double, eqTime: Double) {
            let geomMeanLongSun = Self.normalizeAngle(angle: 280.46646 + jc * (36000.76983 + 0.0003032 * jc))
            let geomMeanAnomalySun = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
            let eccentEarthOrbit = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
            let sunEqOfCenter = sin(Self.deg2rad(geomMeanAnomalySun)) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) + sin(Self.deg2rad(2 * geomMeanAnomalySun)) * (0.019993 - 0.000101 * jc) + sin(Self.deg2rad(3 * geomMeanAnomalySun)) * 0.000289
            let sunTrueLong = geomMeanLongSun + sunEqOfCenter
            let sunAppLong = sunTrueLong - 0.00569 - 0.00478 * sin(Self.deg2rad(125.04 - 1934.136 * jc))
            let meanObliqEcliptic = 23.0 + (26.0 + ((21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0)) / 60.0
            let obliqCorr = meanObliqEcliptic + 0.00256 * cos(Self.deg2rad(125.04 - 1934.136 * jc))
            let declination = Self.rad2deg(asin(sin(Self.deg2rad(obliqCorr)) * sin(Self.deg2rad(sunAppLong))))
            let y = tan(Self.deg2rad(obliqCorr / 2.0)) * tan(Self.deg2rad(obliqCorr / 2.0))
            let eqTime = 4.0 * Self.rad2deg(y * sin(2.0 * Self.deg2rad(geomMeanLongSun)) - 2.0 * eccentEarthOrbit * sin(Self.deg2rad(geomMeanAnomalySun)) + 4.0 * eccentEarthOrbit * y * sin(Self.deg2rad(geomMeanAnomalySun)) * cos(2.0 * Self.deg2rad(geomMeanLongSun)) - 0.5 * y * y * sin(4.0 * Self.deg2rad(geomMeanLongSun)) - 1.25 * eccentEarthOrbit * eccentEarthOrbit * sin(2.0 * Self.deg2rad(geomMeanAnomalySun)));
            return (declination, eqTime)
        }

        let coords = solarCoordinates(jc: julianCentury)
        let haSunrise = Self.hourAngleSunrise(latitude: coordinate.latitude, declination: coords.declination)
        guard !haSunrise.isNaN else { return SunTimes(sunrise: nil, sunset: nil) }

        let solarNoon = (720 - coords.eqTime - 4 * coordinate.longitude + Double(timeZone.secondsFromGMT(for: dayStart)) / 60.0) / 1440.0
        let sunriseTime = solarNoon - haSunrise * 4.0 / 1440.0
        let sunsetTime = solarNoon + haSunrise * 4.0 / 1440.0

        func date(for fractionalDay: Double) -> Date? {
            let seconds = fractionalDay * 86400.0
            return calendar.date(byAdding: .second, value: Int(seconds.rounded()), to: dayStart)
        }

        return SunTimes(sunrise: date(for: sunriseTime), sunset: date(for: sunsetTime))
    }

    private static func julianDay(for date: Date, timeZone: TimeZone) -> Double {
        let seconds = Double(timeZone.secondsFromGMT(for: date))
        let utcDate = date.addingTimeInterval(-seconds)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let comps = calendar.dateComponents([.year, .month, .day], from: utcDate)
        let y = Double(comps.year!)
        let m = Double(comps.month!)
        let d = Double(comps.day!)
        let a = floor((14 - m) / 12)
        let yPrime = y + 4800 - a
        let mPrime = m + 12 * a - 3
        return d + floor((153 * mPrime + 2) / 5) + 365 * yPrime + floor(yPrime / 4) - floor(yPrime / 100) + floor(yPrime / 400) - 32045.0
    }

    private static func hourAngleSunrise(latitude: Double, declination: Double) -> Double {
        let latRad = deg2rad(latitude)
        let declRad = deg2rad(declination)
        let solarZenith = deg2rad(90.833) // includes refraction + sun radius
        let cosH = (cos(solarZenith) - sin(latRad) * sin(declRad)) / (cos(latRad) * cos(declRad))
        return acos(cosH)
    }

    private static func normalizeAngle(angle: Double) -> Double {
        var result = fmod(angle, 360.0)
        if result < 0 { result += 360.0 }
        return result
    }

    private static func deg2rad(_ deg: Double) -> Double { deg * .pi / 180.0 }
    private static func rad2deg(_ rad: Double) -> Double { rad * 180.0 / .pi }
}
