import SwiftUI
import WidgetKit

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshotFile
}

/// `widget.json` now, and the same shelf at the next local midnight with today not yet read: the app republishes after
/// every change and at midnight while it runs, but it may not be running then. The app's reload replaces both entries.
struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: .now, snapshot: .empty) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: .now, snapshot: .load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        let snapshot = WidgetSnapshotFile.load()
        let midnight = Calendar.current.startOfDay(for: .now.addingTimeInterval(24 * 60 * 60))
        let entries = [SnapshotEntry(date: .now, snapshot: snapshot), SnapshotEntry(date: midnight, snapshot: snapshot.atMidnight())]
        completion(Timeline(entries: entries, policy: .never))
    }
}

@main
struct StashWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "StashWidget", provider: SnapshotProvider()) { entry in
            StashWidgetView(snapshot: entry.snapshot)
        }
        .configurationDisplayName("Stash")
        .description("Your next unread links and your reading streak.")
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}

/// The handoff's `StashWidget`. Medium: header (icon, wordmark, candle and streak, unread) and up to 3 rows that open
/// the reader. Small: unread count, streak and the next title. Empty: "All caught up. Nothing waiting on your shelf."
struct StashWidgetView: View {
    let snapshot: WidgetSnapshotFile

    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let c = StashPalette.of(scheme)
        Group {
            if family == .systemSmall { small(c) } else { medium(c) }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(c.surface, for: .widget)
    }

    private func medium(_ c: StashPalette) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                AppIconTile()
                Text(verbatim: "Stash").font(.playfair(17)).foregroundStyle(c.primary).frame(maxWidth: .infinity, alignment: .leading)
                streak(c, candle: 14)
                Text("· \(snapshot.unreadCount) unread").font(.inter(12)).foregroundStyle(c.faded)
            }
            if snapshot.items.isEmpty {
                caughtUp(c).font(.lora(14, .semibold)).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            ForEach(snapshot.items.prefix(3), id: \.id) { item in
                Link(destination: WidgetSnapshotFile.readerURL(item.id)) { row(item, c) }
            }
        }
    }

    private func row(_ item: WidgetSnapshotFile.Item, _ c: StashPalette) -> some View {
        HStack(spacing: 10) {
            Monogram(item: item, palette: c)
            VStack(alignment: .leading, spacing: 1) {
                Text(item.title).font(.lora(13, .semibold)).foregroundStyle(c.ink).lineLimit(1)
                Text(meta(item)).font(.inter(11)).foregroundStyle(c.faded).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func small(_ c: StashPalette) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 3) {
                Text(verbatim: "Stash").font(.playfair(15)).foregroundStyle(c.primary).frame(maxWidth: .infinity, alignment: .leading)
                streak(c, candle: 13)
            }
            Text(verbatim: "\(snapshot.unreadCount)").font(.playfair(40)).foregroundStyle(c.ink)
            Text("unread").font(.inter(11)).foregroundStyle(c.faded)
            Spacer(minLength: 4)
            Group {
                if let next = snapshot.items.first { Text(next.title) } else { caughtUp(c) }
            }
            .font(.lora(12, .semibold)).foregroundStyle(c.ink).lineLimit(2)
        }
        .widgetURL(snapshot.items.first.map { WidgetSnapshotFile.readerURL($0.id) })
    }

    /// Lit candle and ink count once something was read today, unlit and faded otherwise.
    // shortcut: no "at risk" look after 9:30 PM (the timeline has one entry), add a dated entry when the widget needs it.
    private func streak(_ c: StashPalette, candle: CGFloat) -> some View {
        HStack(spacing: 3) {
            // Unlit: color-input-border, the handoff's token for boundaries that must stay visible (3:1) in both themes;
            // the app chip's outline colour all but disappears on the dark widget.
            Candle(wax: c.faded, flame: snapshot.readToday ? c.flame : c.inputBorder).frame(width: candle, height: candle)
            Text(verbatim: "\(snapshot.streak)").font(.inter(12, .bold)).foregroundStyle(snapshot.readToday ? c.ink : c.faded)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("\(snapshot.streak)-day reading streak"))
    }

    private func caughtUp(_ c: StashPalette) -> some View {
        Text("All caught up. Nothing waiting on your shelf.").foregroundStyle(c.faded).multilineTextAlignment(.center)
    }

    private func meta(_ item: WidgetSnapshotFile.Item) -> String {
        guard let minutes = item.readingMinutes else { return item.siteName }
        return "\(item.siteName) · " + String(localized: "\(minutes) min read")
    }
}

/// The Leather app icon in miniature (the app's AppIconTile at 22 pt).
private struct AppIconTile: View {
    var body: some View {
        Text(verbatim: "S").font(.playfair(14)).foregroundStyle(Color(hex: 0xFBF6EA))
            .frame(width: 22, height: 22)
            .background(Color(hex: 0x7A4E2D), in: RoundedRectangle(cornerRadius: 7))
            .overlay(RoundedRectangle(cornerRadius: 7).stroke(Color(hex: 0x5E3A20), lineWidth: 1))
            .accessibilityHidden(true)
    }
}

/// The 34 pt thumbnail: the site's initial (a play triangle for videos). Remote images are not loaded: a widget
/// cannot fetch them while rendering.
private struct Monogram: View {
    let item: WidgetSnapshotFile.Item
    let palette: StashPalette

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8).fill(palette.variant)
            if item.contentType == "Video" {
                Path { p in
                    p.move(to: CGPoint(x: 1.1, y: 0))
                    p.addLine(to: CGPoint(x: 9.3, y: 4.65))
                    p.addLine(to: CGPoint(x: 1.1, y: 9.3))
                    p.closeSubpath()
                }
                .fill(palette.ink).frame(width: 9.3, height: 9.3)
            } else {
                Text(verbatim: String(item.siteName.prefix(1)).uppercased()).font(.lora(15, .semibold)).foregroundStyle(palette.ink)
            }
        }
        .frame(width: 34, height: 34)
        .accessibilityHidden(true)
    }
}

/// The candle from the app's streak chip (24-unit icon grid): a wax body with a wick and a flame.
private struct Candle: View {
    let wax: Color
    let flame: Color

    var body: some View {
        Canvas { context, size in
            let k = size.width / 24
            context.scaleBy(x: k, y: k)
            context.fill(Path(roundedRect: CGRect(x: 9, y: 11, width: 6, height: 11), cornerRadius: 1.2), with: .color(wax))
            context.stroke(Path { $0.move(to: CGPoint(x: 12, y: 9.6)); $0.addLine(to: CGPoint(x: 12, y: 11.2)) }, with: .color(wax), lineWidth: 1.2)
            // FLAME from the app's icons: M12 1.8c1.9 2.3 2.8 4 2.8 5.4A2.8 2.8 0 0 1 12 10a2.8 2.8 0 0 1-2.8-2.8c0-1.4.9-3.1 2.8-5.4z
            context.fill(Path { p in
                p.move(to: CGPoint(x: 12, y: 1.8))
                p.addCurve(to: CGPoint(x: 14.8, y: 7.2), control1: CGPoint(x: 13.9, y: 4.1), control2: CGPoint(x: 14.8, y: 5.8))
                p.addArc(center: CGPoint(x: 12, y: 7.2), radius: 2.8, startAngle: .degrees(0), endAngle: .degrees(180), clockwise: false)
                p.addCurve(to: CGPoint(x: 12, y: 1.8), control1: CGPoint(x: 9.2, y: 5.8), control2: CGPoint(x: 10.1, y: 4.1))
                p.closeSubpath()
            }, with: .color(flame))
        }
        .accessibilityHidden(true)
    }
}

#Preview("Medium", as: .systemMedium) {
    StashWidget()
} timeline: {
    SnapshotEntry(date: .now, snapshot: WidgetSnapshotFile(items: [
        .init(id: "1", title: "The quiet return of serif type on screens", siteName: "example-blog.com", contentType: "Article", readingMinutes: 5, imageUrl: nil),
        .init(id: "2", title: "Bookbinding by hand", siteName: "youtube.com", contentType: "Video", readingMinutes: 12, imageUrl: nil),
    ], unreadCount: 7, streak: 12, readToday: true))
    SnapshotEntry(date: .now, snapshot: .empty)
}
