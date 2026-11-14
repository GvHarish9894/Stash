import SwiftUI

/// The handoff's colour tokens (Parchment light, Night library dark) for the Share and Widget extensions, which cannot
/// use the Compose theme. Primary is the default Leather accent: the extensions do not read the app's settings.
struct StashPalette {
    let bg, surface, variant, outline, inputBorder, ink, faded, primary, onPrimary, brass, danger, flame: Color

    static let light = StashPalette(
        bg: Color(hex: 0xF4ECD8), surface: Color(hex: 0xFAF4E6), variant: Color(hex: 0xE8DCC0), outline: Color(hex: 0xCBB894),
        inputBorder: Color(hex: 0x857158),
        ink: Color(hex: 0x3B2F23), faded: Color(hex: 0x6E5D49), primary: Color(hex: 0x7A4E2D), onPrimary: Color(hex: 0xFBF6EA),
        brass: Color(hex: 0xB8893B), danger: Color(hex: 0x8B2E2A), flame: Color(hex: 0xE0B45C),
    )
    static let dark = StashPalette(
        bg: Color(hex: 0x1E1812), surface: Color(hex: 0x2A2219), variant: Color(hex: 0x3D3226), outline: Color(hex: 0x4A3D2E),
        inputBorder: Color(hex: 0x8A7861),
        ink: Color(hex: 0xEADFC8), faded: Color(hex: 0xB8A88E), primary: Color(hex: 0xD2A26A), onPrimary: Color(hex: 0x1E1812),
        brass: Color(hex: 0xE0B45C), danger: Color(hex: 0xD97A6F), flame: Color(hex: 0xE0B45C),
    )

    static func of(_ scheme: ColorScheme) -> StashPalette { scheme == .dark ? dark : light }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// The handoff's fonts, bundled into each extension from core/resources/src/commonMain/composeResources/font (UIAppFonts).
/// `custom(_:size:)` scales with Dynamic Type.
extension Font {
    static func lora(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(weight == .bold ? "Lora-Bold" : weight == .semibold ? "Lora-SemiBold" : "Lora-Regular", size: size)
    }

    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(weight == .bold ? "Inter-Bold" : weight == .semibold ? "Inter-SemiBold" : "Inter-Regular", size: size)
    }

    static func playfair(_ size: CGFloat) -> Font { .custom("PlayfairDisplay-BoldItalic", size: size) }
}
