import SwiftUI

struct RootView: View {
    @EnvironmentObject private var homeViewModel: HomeViewModel
    @EnvironmentObject private var settingsViewModel: SettingsViewModel
    @StateObject private var onboardingViewModel: OnboardingViewModel

    init(onboardingViewModel: OnboardingViewModel) {
        _onboardingViewModel = StateObject(wrappedValue: onboardingViewModel)
    }

    var body: some View {
        Group {
            if onboardingViewModel.onboardingComplete {
                NavigationStack {
                    HomeView()
                        .toolbar {
                            NavigationLink("Settings") {
                                SettingsView()
                            }
                        }
                }
                .task {
                    await homeViewModel.refreshIfNeeded()
                }
            } else if onboardingViewModel.isLoaded {
                OnboardingView(viewModel: onboardingViewModel) {
                    onboardingViewModel.onboardingComplete = true
                }
            } else {
                ProgressView()
            }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject private var homeViewModel: HomeViewModel

    var body: some View {
        List {
            Section("Today") {
                HStack {
                    Text("Sunrise")
                    Spacer()
                    Text(homeViewModel.todaySunriseString)
                }
                HStack {
                    Text("Sunset")
                    Spacer()
                    Text(homeViewModel.todaySunsetString)
                }
            }

            Section("Alarms") {
                Toggle("Sunrise Alarm", isOn: $homeViewModel.sunriseConfig.enabled)
                Stepper(value: $homeViewModel.sunriseConfig.offsetMinutes, in: -180...180) {
                    Text("Offset: \(homeViewModel.sunriseConfig.offsetMinutes) min")
                }
                Toggle("Sunset Alarm", isOn: $homeViewModel.sunsetConfig.enabled)
                Stepper(value: $homeViewModel.sunsetConfig.offsetMinutes, in: -180...180) {
                    Text("Offset: \(homeViewModel.sunsetConfig.offsetMinutes) min")
                }
                Button("Recompute & Reschedule") {
                    Task { await homeViewModel.rescheduleNow() }
                }
            }
        }
        .navigationTitle("Suntime Alerts")
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settingsViewModel: SettingsViewModel

    var body: some View {
        Form {
            Section("Location") {
                Picker("Mode", selection: $settingsViewModel.locationMode) {
                    ForEach(LocationMode.allCases, id: \.self) { mode in
                        Text(mode.description).tag(mode)
                    }
                }
                if case .fixed(let lat, let lon) = settingsViewModel.locationMode {
                    Text("Lat: \(lat), Lon: \(lon)")
                }
            }

            Section("Preferences") {
                Toggle("24h time", isOn: $settingsViewModel.timeFormat24h)
            }
        }
        .navigationTitle("Settings")
    }
}
