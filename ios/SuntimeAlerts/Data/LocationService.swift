import CoreLocation
import Foundation

protocol LocationServiceProtocol {
    func currentCoordinate() async throws -> Coordinate
}

final class CoreLocationService: NSObject, LocationServiceProtocol {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<Coordinate, Error>?

    override init() {
        super.init()
        manager.delegate = self
    }

    func currentCoordinate() async throws -> Coordinate {
        if let location = manager.location {
            return Coordinate(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude)
        }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Coordinate, Error>) in
            self.continuation = continuation
            manager.requestWhenInUseAuthorization()
            manager.requestLocation()
        }
    }
}

extension CoreLocationService: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.first else { return }
        continuation?.resume(returning: Coordinate(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude))
        continuation = nil
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }
}

// Alias type used in view models
typealias LocationService = CoreLocationService
