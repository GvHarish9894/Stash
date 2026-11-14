import XCTest

final class InboxWriterTests: XCTestCase {
    private var inbox: URL!

    override func setUpWithError() throws {
        inbox = FileManager.default.temporaryDirectory.appendingPathComponent("inbox-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: inbox)
    }

    /// The share files the app would drain: final names only, in name order.
    private func shareFiles() throws -> [URL] {
        try FileManager.default.contentsOfDirectory(at: inbox, includingPropertiesForKeys: nil)
            .filter { !$0.lastPathComponent.hasPrefix(".") && $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    }

    func testWritesOneCompleteFileNamedByTimeAndCreatesTheInbox() throws {
        let at = Date(timeIntervalSince1970: 1_762_941_600.123)
        let file = try InboxWriter.write(text: "Read this https://example.com/a?b=1", subject: "A title", sourceApp: nil, at: at, to: inbox)

        XCTAssertEqual(try shareFiles(), [file])
        XCTAssertTrue(file.lastPathComponent.hasPrefix("001762941600123-"), file.lastPathComponent)
        let entry = try JSONDecoder().decode(InboxEntry.self, from: Data(contentsOf: file))
        XCTAssertEqual(entry, InboxEntry(text: "Read this https://example.com/a?b=1", subject: "A title", sourceApp: nil, at: "2025-11-12T10:00:00.123Z"))
        let json = try XCTUnwrap(String(data: Data(contentsOf: file), encoding: .utf8))
        XCTAssertFalse(json.contains("sourceApp"), "absent fields are left out, which the app reads as null")
        XCTAssertFalse(json.contains("\n"), "one JSON object per file")
    }

    /// Fixtures/inbox-entry.json is also drained by the Kotlin reader test (AppGroupShareInboxTest).
    func testWritesWhatTheSharedFixtureHolds() throws {
        let file = try InboxWriter.write(
            text: "Read this https://example.com/a?b=1", subject: "A title — “quoted” ✂️", sourceApp: nil,
            at: Date(timeIntervalSince1970: 1_762_941_600.123), to: inbox,
        )
        let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "inbox-entry", withExtension: "json"))
        let written = try JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? NSDictionary
        let expected = try JSONSerialization.jsonObject(with: Data(contentsOf: fixture)) as? NSDictionary
        XCTAssertEqual(written, expected)
    }

    func testNamesSortInTheOrderShared() throws {
        let start = Date(timeIntervalSince1970: 1_762_941_600)
        let names = try (0..<20).map { i in
            try InboxWriter.write(text: "https://example.com/\(i)", subject: nil, sourceApp: nil, at: start + Double(i) / 1000, to: inbox).lastPathComponent
        }
        XCTAssertEqual(try shareFiles().map(\.lastPathComponent), names)
    }

    /// Writers on many threads while the app drains: every file it sees is complete JSON, and no share is lost.
    func testConcurrentWritesWhileAReaderDrainsNeverExposeAPartialFile() throws {
        try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        let writers = 8, perWriter = 50
        let done = DispatchGroup()
        var writeErrors = [Error]()
        let lock = NSLock()
        for w in 0..<writers {
            DispatchQueue.global().async(group: done) {
                for i in 0..<perWriter {
                    do {
                        try InboxWriter.write(text: "https://example.com/\(w)/\(i)", subject: String(repeating: "x", count: 2_000), sourceApp: nil, to: self.inbox)
                    } catch {
                        lock.lock(); writeErrors.append(error); lock.unlock()
                    }
                }
            }
        }
        var drained = Set<String>()
        var finished = false
        repeat {
            finished = done.wait(timeout: .now()) == .success
            for file in try shareFiles() {
                let entry = try JSONDecoder().decode(InboxEntry.self, from: Data(contentsOf: file))
                drained.insert(try XCTUnwrap(entry.text))
                try FileManager.default.removeItem(at: file)
            }
        } while try !finished || !shareFiles().isEmpty

        XCTAssertTrue(writeErrors.isEmpty, "\(writeErrors)")
        XCTAssertEqual(drained.count, writers * perWriter)
        let leftovers = try FileManager.default.contentsOfDirectory(atPath: inbox.path)
        XCTAssertEqual(leftovers, [], "no temp file is left behind")
    }
}
