import Foundation

/// One share as the app reads it: `{"text", "subject", "sourceApp", "at"}`, `at` in ISO-8601 UTC. Mirrors
/// `AppGroupShareInbox` in core/platform/src/iosMain, which drains the inbox.
struct InboxEntry: Codable, Equatable {
    let text: String?
    let subject: String?
    let sourceApp: String?
    let at: String
}

/// Hands a share to the app through the App Group (decision D26): `inbox/` holds one JSON file per share. Each is
/// written to a dot-prefixed temp name and then renamed to `<epoch millis, 15 digits>-<UUID>.json`, so the app never
/// reads a partial file, names sort in the order shared, and no lock is held by a process iOS may suspend.
enum InboxWriter {
    static let appGroup = "group.com.techgv.stash"
    /// Darwin notification the app observes (`iosApp/iosApp/iOSApp.swift`) to drain while it is running.
    static let inboxChanged = "com.techgv.stash.inbox-changed"

    static var inboxDirectory: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup)?.appendingPathComponent("inbox", isDirectory: true)
    }

    /// Writes one share into [directory] and returns its file.
    @discardableResult
    static func write(text: String?, subject: String?, sourceApp: String?, at date: Date = Date(), to directory: URL) throws -> URL {
        let files = FileManager.default
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        let entry = InboxEntry(text: text, subject: subject, sourceApp: sourceApp, at: timestamp.string(from: date))
        let encoder = JSONEncoder()
        encoder.outputFormatting = .withoutEscapingSlashes
        let id = UUID().uuidString
        let temp = directory.appendingPathComponent(".\(id).tmp")
        let millis = Int64((date.timeIntervalSince1970 * 1000).rounded())
        let final = directory.appendingPathComponent(String(format: "%015lld", millis) + "-\(id).json")
        do {
            try encoder.encode(entry).write(to: temp)
            try files.moveItem(at: temp, to: final)
        } catch {
            try? files.removeItem(at: temp)
            throw error
        }
        return final
    }

    static func notifyApp() {
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName(inboxChanged as CFString), nil, nil, true)
    }

    private static let timestamp: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
