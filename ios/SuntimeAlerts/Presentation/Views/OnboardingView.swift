import SwiftUI

struct OnboardingView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    var onFinished: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: AppSpacing.xl) {
                Text(viewModel.step.title)
                    .font(AppTypography.headline)
                    .foregroundColor(AppColors.textPrimary)

                content

                Spacer()

                HStack {
                    if viewModel.step != .welcome {
                        Button("Back") { viewModel.back() }
                            .buttonStyle(.bordered)
                    }
                    Spacer()
                    Button(viewModel.step == .summary ? "Save & Start" : "Next") {
                        Task {
                            if viewModel.step == .summary {
                                await viewModel.finish()
                                onFinished()
                            } else {
                                viewModel.next()
                            }
                        }
                    }
                    .disabled(!viewModel.canAdvance)
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()
            .background(AppColors.background.ignoresSafeArea())
            .toolbar { ToolbarItem(placement: .principal) { Text("Onboarding").foregroundStyle(AppColors.textSecondary) } }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.step {
        case .welcome:
            VStack(alignment: .leading, spacing: AppSpacing.m) {
                Text("Suntime Alerts keeps you on time with sunrise and sunset.")
                    .foregroundColor(AppColors.textSecondary)
                Text("We will guide you through location, notifications, and alarm setup.")
                    .foregroundColor(AppColors.textSecondary)
            }
        case .location:
            VStack(alignment: .leading, spacing: AppSpacing.m) {
                Text("Choose how we determine your location")
                    .foregroundColor(AppColors.textPrimary)
                HStack(spacing: AppSpacing.s) {
                    Button(action: { viewModel.locationMode = .device }) {
                        Text("Device")
                            .padding(.vertical, AppSpacing.xs)
                            .padding(.horizontal, AppSpacing.m)
                            .background(viewModel.locationMode == .device ? AppColors.sunriseAccent.opacity(0.2) : AppColors.surfaceSecondary)
                            .foregroundColor(AppColors.textPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.cornerRadius))
                    }
                    Button(action: { viewModel.locationMode = .fixed(latitude: Double(viewModel.fixedLatitude) ?? 0, longitude: Double(viewModel.fixedLongitude) ?? 0) }) {
                        Text("Manual")
                            .padding(.vertical, AppSpacing.xs)
                            .padding(.horizontal, AppSpacing.m)
                            .background({ () -> Color in
                                if case .fixed = viewModel.locationMode { return AppColors.sunsetAccent.opacity(0.2) }
                                return AppColors.surfaceSecondary
                            }())
                            .foregroundColor(AppColors.textPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: AppSpacing.cornerRadius))
                    }
                }

                if case .fixed = viewModel.locationMode {
                    VStack(alignment: .leading, spacing: AppSpacing.s) {
                        Text("Manual coordinates")
                            .foregroundColor(AppColors.textSecondary)
                        HStack {
                            TextField("Latitude", text: $viewModel.fixedLatitude)
                                .keyboardType(.decimalPad)
                                .textFieldStyle(.roundedBorder)
                            TextField("Longitude", text: $viewModel.fixedLongitude)
                                .keyboardType(.decimalPad)
                                .textFieldStyle(.roundedBorder)
                        }
                    }
                }
            }
        case .notifications:
            VStack(alignment: .leading, spacing: AppSpacing.m) {
                Text("Enable alerts to receive sunrise and sunset reminders.")
                    .foregroundColor(AppColors.textSecondary)
                Toggle("Allow notifications", isOn: $viewModel.notificationsEnabled)
                    .toggleStyle(.switch)
            }
        case .alarms:
            VStack(alignment: .leading, spacing: AppSpacing.m) {
                Text("Configure your initial alarms")
                    .foregroundColor(AppColors.textPrimary)
                Toggle("Sunrise alarm", isOn: $viewModel.sunriseEnabled)
                Stepper("Sunrise offset: \(viewModel.sunriseOffsetMinutes) min", value: $viewModel.sunriseOffsetMinutes, in: -180...180)
                Toggle("Sunset alarm", isOn: $viewModel.sunsetEnabled)
                Stepper("Sunset offset: \(viewModel.sunsetOffsetMinutes) min", value: $viewModel.sunsetOffsetMinutes, in: -180...180)
            }
        case .summary:
            VStack(alignment: .leading, spacing: AppSpacing.m) {
                Text("You are ready to go")
                    .foregroundColor(AppColors.textPrimary)
                Text("Location: \(viewModel.locationMode.description)")
                    .foregroundColor(AppColors.textSecondary)
                Text("Sunrise alarm: \(viewModel.sunriseEnabled ? "On" : "Off") @ \(viewModel.sunriseOffsetMinutes) min")
                    .foregroundColor(AppColors.textSecondary)
                Text("Sunset alarm: \(viewModel.sunsetEnabled ? "On" : "Off") @ \(viewModel.sunsetOffsetMinutes) min")
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }
}

struct OnboardingView_Previews: PreviewProvider {
    static var previews: some View {
        OnboardingView(viewModel: OnboardingViewModel(settingsStore: UserDefaultsSettingsStore())) { }
    }
}
