import SwiftUI

enum QuickSaveResult: Equatable {
    /// Written to the inbox; [detail] is the shared page's title or host.
    case saved(detail: String?)
    case nothingToSave
    case failed
}

/// The handoff's quick-save sheet: the link card tucks into a book that closes, a ribbon drops, the text rises.
/// Closes itself after 4.5 s (2 s when there is nothing to save). Reduce Motion shows the end state at once.
struct QuickSaveView: View {
    let result: QuickSaveResult
    let onDone: () -> Void

    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tuck = 0.0
    @State private var cover = 0.0
    @State private var ribbon = 0.0
    @State private var text = 0.0

    private var title: LocalizedStringKey {
        switch result {
        case .saved: "Tucked into your shelf"
        case .nothingToSave: "Nothing to save here"
        case .failed: "Couldn’t save this link"
        }
    }

    // iOS presents a share extension in its own opaque card (a transparent full-screen presentation is ignored, checked
    // on the simulator), so that card is the handoff's sheet: it slides up, and this view fills it with the paper
    // background instead of drawing a second sheet and scrim inside it.
    var body: some View {
        let c = StashPalette.of(scheme)
        VStack(spacing: 14) {
            if case .saved = result {
                Book(palette: c, tuck: tuck, cover: cover, ribbon: ribbon).padding(.top, 6)
            }
            Group {
                Text(title).font(.lora(24, .bold)).foregroundStyle(c.ink).accessibilityAddTraits(.isHeader)
                if case .saved(let detail?) = result {
                    Text(detail).font(.inter(14)).foregroundStyle(c.faded).multilineTextAlignment(.center)
                        .lineLimit(3).frame(maxWidth: 300)
                }
                Button(action: onDone) {
                    Text("Done").font(.inter(14, .semibold)).foregroundStyle(c.onPrimary)
                        .padding(.horizontal, 28).frame(minHeight: 48).background(c.primary, in: Capsule())
                }
            }
            .opacity(text)
            .offset(y: (1 - text) * 8)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(c.bg.ignoresSafeArea())
        .task { await play() }
    }

    private func play() async {
        if reduceMotion {
            (tuck, cover, ribbon, text) = (1, 1, 1, 1)
        } else {
            let out = Animation.timingCurve(0.2, 0.8, 0.2, 1, duration: 0.36)
            withAnimation(.timingCurve(0.5, 0, 0.3, 1, duration: 0.8).delay(0.25)) { tuck = 1 }
            withAnimation(out.speed(0.36 / 0.45).delay(0.85)) { cover = 1 }
            withAnimation(out.speed(0.36 / 0.35).delay(1.2)) { ribbon = 1 }
            withAnimation(.easeOut(duration: 0.35).delay(result == .nothingToSave || result == .failed ? 0.2 : 1.25)) { text = 1 }
        }
        try? await Task.sleep(for: .seconds(result == .nothingToSave || result == .failed ? 2 : 4.5))
        onDone()
    }
}

/// The book from the app's SaveScreen (200 x 180 pt): back cover, the saved page, the front cover, the ribbon.
private struct Book: View {
    let palette: StashPalette
    let tuck: Double
    let cover: Double
    let ribbon: Double

    var body: some View {
        let spine = Color(hex: 0x5E3A20)
        ZStack(alignment: .topLeading) {
            UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 6, bottomTrailingRadius: 12, topTrailingRadius: 12)
                .fill(spine).frame(width: 140, height: 120).offset(x: 30, y: 50)
            Page(palette: palette).modifier(TuckIn(progress: tuck)).offset(x: 52, y: 60)
            ZStack(alignment: .topLeading) {
                UnevenRoundedRectangle(topLeadingRadius: 6, bottomLeadingRadius: 6, bottomTrailingRadius: 12, topTrailingRadius: 12)
                    .fill(palette.primary)
                Rectangle().fill(spine).frame(width: 12, height: 120)
                Rectangle().fill(palette.brass).frame(width: 104, height: 1).offset(x: 22, y: 18)
                Rectangle().fill(palette.brass).frame(width: 104, height: 1).offset(x: 22, y: 102)
            }
            .frame(width: 140, height: 120)
            .shadow(color: .black.opacity(0.18), radius: 9, y: 4)
            .rotation3DEffect(.degrees(-70 * (1 - cover)), axis: (x: 0, y: 1, z: 0), anchor: .leading, perspective: 0.6)
            .offset(x: 30, y: 50)
            Ribbon().fill(palette.danger).frame(width: 18, height: 70)
                .scaleEffect(x: 1, y: ribbon, anchor: .top).offset(x: 132, y: 46)
        }
        .frame(width: 200, height: 180, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

/// The saved page: a small card with two ink lines and a faded one.
private struct Page: View {
    let palette: StashPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Capsule().fill(palette.ink).frame(width: 72, height: 6)
            Capsule().fill(palette.ink).frame(width: 56, height: 6)
            Capsule().fill(palette.outline).frame(width: 40, height: 5)
        }
        .padding(8)
        .frame(width: 96, height: 70, alignment: .topLeading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(palette.outline, lineWidth: 1))
    }
}

/// The page drops in from -70 pt and shrinks, then sinks 40 pt into the book and fades (keyframes from SaveScreen).
private struct TuckIn: ViewModifier, Animatable {
    var progress: Double
    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    func body(content: Content) -> some View {
        let (y, angle, scale, alpha): (Double, Double, Double, Double)
        if progress < 0.55 {
            let k = progress / 0.55
            (y, angle, scale, alpha) = (-70 + 64 * k, -8 + 6 * k, 1 - 0.18 * k, 1)
        } else {
            let k = (progress - 0.55) / 0.45
            (y, angle, scale, alpha) = (-6 + 46 * k, -2 + 2 * k, 0.82 - 0.42 * k, 1 - k)
        }
        return content.scaleEffect(scale).rotationEffect(.degrees(angle)).offset(y: y).opacity(alpha)
    }
}

/// A bookmark ribbon with a notched end.
private struct Ribbon: Shape {
    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: .zero)
            p.addLine(to: CGPoint(x: rect.width, y: 0))
            p.addLine(to: CGPoint(x: rect.width, y: rect.height))
            p.addLine(to: CGPoint(x: rect.width / 2, y: rect.height * 0.82))
            p.addLine(to: CGPoint(x: 0, y: rect.height))
            p.closeSubpath()
        }
    }
}

#Preview("Saved") { QuickSaveView(result: .saved(detail: "The quiet return of serif type on screens"), onDone: {}) }
#Preview("Nothing to save") { QuickSaveView(result: .nothingToSave, onDone: {}) }
