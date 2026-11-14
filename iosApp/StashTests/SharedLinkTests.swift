import XCTest

final class SharedLinkTests: XCTestCase {
    func testFindsAnHttpOrHttpsLinkAnywhereInTheText() {
        [
            "https://example.com",
            "HTTP://EXAMPLE.COM/PATH",
            "Read this: https://example.com/a?b=1#c — so good",
            "(see https://en.wikipedia.org/wiki/Bookbinding_(craft))",
            "Look at https://example.com.",
            "链接：https://例子.中国/路径。很好",
            "https://user@example.com:8443/x",
            "http://[2001:db8::1]/page",
            "not this https:// but https://example.org",
        ].forEach { XCTAssertTrue(SharedLink.containsWebURL($0), $0) }
    }

    func testRejectsTextWithoutAUsableWebLink() {
        [
            nil,
            "",
            "Just some words",
            "example.com without a scheme",
            "ftp://example.com/file",
            "https://",
            "https://.example.com",
            "https://example.com:99999",
            "https://exa\\mple.com",
            "mailto:someone@example.com",
        ].forEach { XCTAssertFalse(SharedLink.containsWebURL($0), $0 ?? "nil") }
    }

    func testReadsSharedTextInEveryFormAppsDeliver() throws {
        let link = "Read https://example.com/a — ✂️"
        XCTAssertEqual(SharedLink.text(from: link as NSString), link)
        XCTAssertEqual(SharedLink.text(from: NSAttributedString(string: link)), link)
        XCTAssertEqual(SharedLink.text(from: Data(link.utf8) as NSData), link)
        let rtf = try NSAttributedString(string: link).data(
            from: NSRange(location: 0, length: (link as NSString).length), documentAttributes: [.documentType: NSAttributedString.DocumentType.rtf],
        )
        XCTAssertEqual(SharedLink.text(from: rtf as NSData), link)
        XCTAssertEqual(SharedLink.text(from: URL(string: "https://example.com/b")! as NSURL), "https://example.com/b")
        XCTAssertNil(SharedLink.text(from: nil))
    }
}
