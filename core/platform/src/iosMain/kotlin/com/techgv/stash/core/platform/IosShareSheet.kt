package com.techgv.stash.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.LinkPresentation.LPLinkMetadata
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

/** `UIActivityViewController` offering the URL, with the title as mail subject and link-preview title. */
@OptIn(ExperimentalForeignApi::class)
class IosShareSheet : ShareSheet {
    override fun share(url: String, title: String) {
        val presenter = topViewController() ?: return
        val item: Any = NSURL.URLWithString(url)?.let { SharedLink(it, title) } ?: url
        val sheet = UIActivityViewController(activityItems = listOf(item), applicationActivities = null)
        // iPad shows the sheet as a popover, which needs an anchor: the centre of the key window, no arrow.
        sheet.popoverPresentationController?.let { popover ->
            val anchor = presenter.view.window ?: presenter.view
            popover.sourceView = anchor
            anchor.bounds.useContents { popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            popover.permittedArrowDirections = 0u
        }
        presenter.presentViewController(sheet, animated = true, completion = null)
    }

    private class SharedLink(private val url: NSURL, private val title: String) : NSObject(), UIActivityItemSourceProtocol {
        override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any = url

        @ObjCSignatureOverride
        override fun activityViewController(activityViewController: UIActivityViewController, itemForActivityType: String?): Any = url

        @ObjCSignatureOverride
        override fun activityViewController(activityViewController: UIActivityViewController, subjectForActivityType: String?): String = title

        // UIKit declares the return type as a forward reference to LinkPresentation's class.
        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "CAST_NEVER_SUCCEEDS")
        override fun activityViewControllerLinkMetadata(activityViewController: UIActivityViewController): objcnames.classes.LPLinkMetadata? =
            LPLinkMetadata().also {
                it.originalURL = url
                it.URL = url
                it.title = title
            } as objcnames.classes.LPLinkMetadata
    }
}
