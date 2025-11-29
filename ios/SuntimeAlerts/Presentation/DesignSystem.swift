import SwiftUI

struct AppColors {
    static let background = Color(red: 0.043, green: 0.114, blue: 0.2) // #0B1D33
    static let surfacePrimary = Color(red: 0.071, green: 0.118, blue: 0.165) // #121E2A
    static let surfaceSecondary = Color(red: 0.094, green: 0.141, blue: 0.208) // #182435
    static let sunriseAccent = Color(red: 0.976, green: 0.658, blue: 0.149) // #F9A826
    static let sunsetAccent = Color(red: 0.353, green: 0.819, blue: 1.0) // #5AD1FF
    static let textPrimary = Color(red: 0.957, green: 0.969, blue: 0.984) // #F4F7FB
    static let textSecondary = Color(red: 0.607, green: 0.694, blue: 0.8) // #9BB1CC
    static let outlineMuted = Color(red: 0.137, green: 0.204, blue: 0.29) // #23344A
}

struct AppSpacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let s: CGFloat = 12
    static let m: CGFloat = 16
    static let l: CGFloat = 20
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let cornerRadius: CGFloat = 12
}

struct AppTypography {
    static let display = Font.system(size: 34, weight: .semibold, design: .rounded)
    static let headline = Font.system(size: 28, weight: .semibold, design: .rounded)
    static let title = Font.system(size: 22, weight: .semibold, design: .rounded)
    static let subtitle = Font.system(size: 18, weight: .medium, design: .rounded)
    static let body = Font.system(size: 16, weight: .regular, design: .rounded)
    static let bodySecondary = Font.system(size: 14, weight: .regular, design: .rounded)
    static let label = Font.system(size: 14, weight: .semibold, design: .rounded)
    static let caption = Font.system(size: 12, weight: .medium, design: .rounded)

    static let time = Font.system(size: 20, weight: .semibold, design: .rounded).monospacedDigit()
}
