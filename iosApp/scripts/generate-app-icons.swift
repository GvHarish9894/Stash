// Generates the iOS app icons from the prototype's AppIconTile composition: the AppIcon background, a centred
// Playfair Display Bold Italic "S" in the AppIcon foreground at 28/48 of the tile, full bleed, no border (iOS masks
// the edge). Colours are read from appIconColors in core/designsystem/src/commonMain/kotlin/com/techgv/stash/core/designsystem/component/Settings.kt.
// Leather is the primary icon (AppIcon.appiconset); Night, Parchment and Ink are alternate icons (AppIcon-<Name>).
//
// Run from the repository root: xcrun swift iosApp/scripts/generate-app-icons.swift

import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

let side = 1024
let glyphRatio = 28.0 / 48.0
let fontURL = URL(fileURLWithPath: "core/resources/src/commonMain/composeResources/font/playfair_bold_italic.ttf")
let modelURL = URL(fileURLWithPath: "core/designsystem/src/commonMain/kotlin/com/techgv/stash/core/designsystem/component/Settings.kt")
let assets = URL(fileURLWithPath: "iosApp/iosApp/Assets.xcassets")

func color(_ hex: String) -> CGColor {
    let v = UInt32(hex, radix: 16)!
    return CGColor(srgbRed: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
}

let model = try String(contentsOf: modelURL, encoding: .utf8)
// AppIcon.Leather -> AppIconColors(Color(0xFF<background>), Color(0xFF<glyph>), Color(0xFF<border>))
let pattern = try NSRegularExpression(pattern: #"AppIcon\.(Leather|Night|Parchment|Ink) -> AppIconColors\(Color\(0xFF([0-9A-F]{6})\), Color\(0xFF([0-9A-F]{6})\)"#)
let icons = pattern.matches(in: model, range: NSRange(model.startIndex..., in: model)).map { m in
    (name: String(model[Range(m.range(at: 1), in: model)!]), bg: String(model[Range(m.range(at: 2), in: model)!]), fg: String(model[Range(m.range(at: 3), in: model)!]))
}
precondition(icons.map(\.name) == ["Leather", "Night", "Parchment", "Ink"], "appIconColors not found in Settings.kt")

let descriptor = (CTFontManagerCreateFontDescriptorsFromURL(fontURL as CFURL) as! [CTFontDescriptor])[0]
let font = CTFontCreateWithFontDescriptor(descriptor, CGFloat(side) * glyphRatio, nil)

for icon in icons {
    let context = CGContext(
        data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue,
    )!
    context.setFillColor(color(icon.bg))
    context.fill(CGRect(x: 0, y: 0, width: side, height: side))
    let text = NSAttributedString(string: "S", attributes: [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color(icon.fg),
    ])
    let line = CTLineCreateWithAttributedString(text)
    // Centre the glyph's ink, not the line box, so the italic S sits optically in the middle.
    let ink = CTLineGetImageBounds(line, context)
    let origin = CGPoint(x: (CGFloat(side) - ink.width) / 2 - ink.minX, y: (CGFloat(side) - ink.height) / 2 - ink.minY)
    context.textPosition = origin
    CTLineDraw(line, context)
    let drawn = ink.offsetBy(dx: origin.x, dy: origin.y)

    let primary = icon.name == "Leather"
    let set = assets.appendingPathComponent(primary ? "AppIcon.appiconset" : "AppIcon-\(icon.name).appiconset")
    try FileManager.default.createDirectory(at: set, withIntermediateDirectories: true)
    let png = set.appendingPathComponent("app-icon-1024.png")
    let destination = CGImageDestinationCreateWithURL(png as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, context.makeImage()!, nil)
    precondition(CGImageDestinationFinalize(destination), "Could not write \(png.path)")
    if !primary {
        let contents = #"{"images":[{"filename":"app-icon-1024.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"author":"xcode","version":1}}"#
        try contents.write(to: set.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
    }
    print("\(icon.name): bg #\(icon.bg) fg #\(icon.fg) glyph x \(Int(drawn.minX))...\(Int(drawn.maxX)) y \(Int(drawn.minY))...\(Int(drawn.maxY)) -> \(png.path)")
}
