import UIKit

/// A port of only the "does this text contain an http(s) URL" part of `LinkCapturePolicy` (core/domain): the same URL
/// pattern, its trailing-punctuation trim, then the host rule of `UrlParts.parse`. The app does the real capture, cleaning and dedupe.
enum SharedLink {
    /// About 1 MB of shared text is kept; the rest is cut before it reaches the inbox.
    static let maxTextCharacters = 1_000_000

    /// Shared text as apps deliver it: a `String`, attributed text, RTF or UTF-8 `Data`, or a URL.
    static func text(from item: NSSecureCoding?) -> String? {
        switch item {
        case let text as String: return text
        case let text as NSAttributedString: return text.string
        case let data as Data where data.starts(with: Data(#"{\rtf"#.utf8)):
            return try? NSAttributedString(data: data, options: [.documentType: NSAttributedString.DocumentType.rtf], documentAttributes: nil).string
        case let data as Data: return String(data: data, encoding: .utf8)
        case let url as URL: return url.absoluteString
        default: return nil
        }
    }

    // Full-width punctuation and brackets end a URL, as in LinkCapturePolicy.urlInText.
    private static let urlInText = try! NSRegularExpression(pattern: #"https?://[^\s<>"。，、；：！？（）【】「」『』]+"#, options: .caseInsensitive)
    private static let hostChars = try! NSRegularExpression(pattern: #"^[\p{L}\p{N}._~-]+$"#)

    static func containsWebURL(_ text: String?) -> Bool {
        guard let text else { return false }
        return urlInText.matches(in: text, range: NSRange(text.startIndex..., in: text)).contains { match in
            hasValidHost(trimTrailing(String(text[Range(match.range, in: text)!])))
        }
    }

    /// Drops sentence punctuation and unbalanced closing brackets that follow a URL in prose.
    private static func trimTrailing(_ raw: String) -> String {
        let pairs: [Character: Character] = [")": "(", "]": "[", "}": "{"]
        var counts = [Character: Int]()
        for c in raw where pairs.keys.contains(c) || pairs.values.contains(c) { counts[c, default: 0] += 1 }
        var result = Substring(raw)
        while let c = result.last {
            if let open = pairs[c] {
                guard counts[c, default: 0] > counts[open, default: 0] else { break }
                counts[c, default: 0] -= 1
            } else if !".,;:!?'”’»…".contains(c) {
                break
            }
            result = result.dropLast()
        }
        return String(result)
    }

    private static func hasValidHost(_ url: String) -> Bool {
        let afterScheme = url[url.range(of: "://")!.upperBound...]
        let authority = afterScheme.prefix { !"/?#".contains($0) }
        if authority.contains("\\") { return false }
        let hostPort = authority.split(separator: "@", omittingEmptySubsequences: false).last ?? ""
        if hostPort.hasPrefix("[") { return hostPort.contains("]") }
        var host = Substring(hostPort.prefix { $0 != ":" })
        while host.hasSuffix(".") { host = host.dropLast() }
        let port = hostPort.contains(":") ? hostPort.split(separator: ":", maxSplits: 1, omittingEmptySubsequences: false)[1] : ""
        let portOK = port.isEmpty || (Int(port).map { (0...65535).contains($0) } ?? false)
        let hostString = String(host)
        return portOK && !hostString.hasPrefix(".") &&
            hostChars.firstMatch(in: hostString, range: NSRange(hostString.startIndex..., in: hostString)) != nil
    }
}
