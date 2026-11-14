import Foundation

/// `widget.json` in the App Group, written by `AppGroupWidgetPublisher` in core/platform/src/iosMain:
/// `{"items": [{"id": String, "title": String, "siteName": String, "contentType": "Article" | "Video" | "Repository",
/// "readingMinutes": Int | null, "imageUrl": String | null}], "unreadCount": Int, "streak": Int, "readToday": Bool}`.
/// `items` holds at most the next 3 unread items; empty means "all caught up".
struct WidgetSnapshotFile: Codable, Equatable {
    struct Item: Codable, Equatable {
        let id: String
        let title: String
        let siteName: String
        let contentType: String
        let readingMinutes: Int?
        let imageUrl: String?
    }

    let items: [Item]
    let unreadCount: Int
    let streak: Int
    let readToday: Bool

    static let empty = WidgetSnapshotFile(items: [], unreadCount: 0, streak: 0, readToday: false)

    static func decode(_ data: Data) throws -> WidgetSnapshotFile { try JSONDecoder().decode(WidgetSnapshotFile.self, from: data) }

    /// The app's last snapshot, or [empty] before the app has published one.
    static func load() -> WidgetSnapshotFile {
        guard let file = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.techgv.stash")?
            .appendingPathComponent("widget.json"),
            let data = try? Data(contentsOf: file),
            let snapshot = try? decode(data) else { return .empty }
        return snapshot
    }

    /// The same shelf after midnight: nothing read yet today. A day that ended without a read while something was
    /// unread breaks the streak; a day that ended with nothing unread is a rest day and keeps it (decision D13).
    func atMidnight() -> WidgetSnapshotFile {
        WidgetSnapshotFile(items: items, unreadCount: unreadCount, streak: !readToday && unreadCount > 0 ? 0 : streak, readToday: false)
    }

    /// `stash://reader/{id}`, handled by the app's `onOpenURL`.
    static func readerURL(_ id: String) -> URL { URL(string: "stash://reader/\(id)")! }
}
