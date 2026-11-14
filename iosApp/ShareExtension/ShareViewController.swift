import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// The Share Extension's entry point (NSExtensionPrincipalClass). It never opens the database or links the Kotlin
/// framework: it writes the share to the App Group inbox, pokes the app, and shows the quick-save sheet.
@objc(ShareViewController)
final class ShareViewController: UIViewController {
    private var finished = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        Task { show(await save()) }
    }

    private func save() async -> QuickSaveResult {
        let items = extensionContext?.inputItems.compactMap { $0 as? NSExtensionItem } ?? []
        var texts = [String]()
        var subject: String?
        for item in items {
            subject = subject ?? [item.attributedContentText?.string, item.attributedTitle?.string]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
            for provider in item.attachments ?? [] {
                if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
                   let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                    texts.append(url.absoluteString)
                } else if provider.hasItemConformingToTypeIdentifier(UTType.text.identifier),
                          let text = SharedLink.text(from: try? await provider.loadItem(forTypeIdentifier: UTType.text.identifier)) {
                    texts.append(text)
                }
            }
        }
        // A link is near the start of real shares; a huge text (a whole document) is cut, and the app caps titles anyway.
        let text = texts.isEmpty ? nil : String(texts.joined(separator: " ").prefix(SharedLink.maxTextCharacters))
        guard SharedLink.containsWebURL(text) || SharedLink.containsWebURL(subject) else { return .nothingToSave }
        guard let inbox = InboxWriter.inboxDirectory,
              (try? InboxWriter.write(text: text, subject: subject, sourceApp: nil, to: inbox)) != nil else { return .failed }
        InboxWriter.notifyApp()
        return .saved(detail: subject ?? texts.compactMap { URL(string: $0)?.host() }.first)
    }

    private func show(_ result: QuickSaveResult) {
        let sheet = UIHostingController(rootView: QuickSaveView(result: result) { [weak self] in
            // Done and the auto-close timer can both fire.
            guard let self, !self.finished else { return }
            self.finished = true
            self.extensionContext?.completeRequest(returningItems: nil)
        })
        sheet.view.backgroundColor = .clear
        addChild(sheet)
        sheet.view.frame = view.bounds
        sheet.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(sheet.view)
        sheet.didMove(toParent: self)
    }
}
