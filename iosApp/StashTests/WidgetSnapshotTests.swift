import XCTest

final class WidgetSnapshotTests: XCTestCase {
    /// widget.json is the fixture AppGroupWidgetPublisherTest (Kotlin) checks the publisher's output against.
    func testDecodesTheSharedFixture() throws {
        let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "widget", withExtension: "json"))
        let snapshot = try WidgetSnapshotFile.decode(Data(contentsOf: fixture))
        XCTAssertEqual(snapshot, WidgetSnapshotFile(
            items: [
                .init(
                    id: "019a7b2c-5e10-7d3a-9c41-2f6e8b1d0a01", title: "The quiet return of serif type on screens",
                    siteName: "example-blog.com", contentType: "Article", readingMinutes: 5, imageUrl: "https://example-blog.com/serif.png",
                ),
                .init(
                    id: "019a7b2c-5e10-7d3a-9c41-2f6e8b1d0a02", title: "Bookbinding by hand — “Coptic” stitch, ✂️ and all",
                    siteName: "youtube.com", contentType: "Video", readingMinutes: nil, imageUrl: nil,
                ),
            ],
            unreadCount: 7, streak: 12, readToday: true,
        ))
        XCTAssertEqual(WidgetSnapshotFile.readerURL(snapshot.items[0].id).absoluteString, "stash://reader/019a7b2c-5e10-7d3a-9c41-2f6e8b1d0a01")
    }

    func testAnEmptySnapshotDecodes() throws {
        let json = #"{"items":[],"readToday":false,"streak":0,"unreadCount":0}"#
        XCTAssertEqual(try WidgetSnapshotFile.decode(Data(json.utf8)), .empty)
    }

    func testFieldsAWidgetDoesNotKnowAreIgnored() throws {
        let json = #"{"items":[{"id":"a","title":"T","siteName":"s","contentType":"Podcast","readingMinutes":3,"imageUrl":null,"tint":1}],"readToday":true,"streak":4,"unreadCount":1,"accent":"Forest"}"#
        let snapshot = try WidgetSnapshotFile.decode(Data(json.utf8))
        XCTAssertEqual(snapshot.items.map(\.contentType), ["Podcast"])
        XCTAssertEqual(snapshot.streak, 4)
    }

    func testAtMidnightNothingIsReadAndAnUnreadShelfBreaksTheStreak() {
        let item = WidgetSnapshotFile.Item(id: "a", title: "T", siteName: "s", contentType: "Article", readingMinutes: nil, imageUrl: nil)
        let readToday = WidgetSnapshotFile(items: [item], unreadCount: 1, streak: 5, readToday: true)
        XCTAssertEqual(readToday.atMidnight(), WidgetSnapshotFile(items: [item], unreadCount: 1, streak: 5, readToday: false))
        let missed = WidgetSnapshotFile(items: [item], unreadCount: 1, streak: 5, readToday: false)
        XCTAssertEqual(missed.atMidnight().streak, 0)
        let restDay = WidgetSnapshotFile(items: [], unreadCount: 0, streak: 5, readToday: false)
        XCTAssertEqual(restDay.atMidnight().streak, 5)
    }
}
