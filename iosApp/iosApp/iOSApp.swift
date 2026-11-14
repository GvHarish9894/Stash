import SwiftUI
import Shared
import UserNotifications
import WidgetKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor private var appDelegate: AppDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // WidgetCenter has no Objective-C API, so the Kotlin widget publisher calls back into Swift for the reload.
        Koin_iosKt.doInitKoin(reloadWidgets: { WidgetCenter.shared.reloadAllTimelines() })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    if let id = DeepLinks.shared.itemIdOf(url: url.absoluteString) { openReader(id) }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { AppEventsKt.onForeground() }
        }
    }
}

/// `stash://reader/{id}` from the widget, a notification or `onOpenURL`: the navigation host observes `DeepLinks.pending`.
func openReader(_ itemId: String) {
    DeepLinks.shared.open(itemId: itemId)
    #if DEBUG
    NSLog("Stash: deep link pending=%@", DeepLinks.shared.pending.value as? String ?? "nil")
    #endif
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Must match the Share Extension's `InboxWriter.inboxChanged`.
    private static let inboxChanged = "com.techgv.stash.inbox-changed"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        // Set before launch finishes so a tap that launched the app is delivered.
        UNUserNotificationCenter.current().delegate = self
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(), nil, { _, _, _, _, _ in AppEventsKt.onForeground() },
            AppDelegate.inboxChanged as CFString, nil, .deliverImmediately,
        )
        for name in [UIApplication.significantTimeChangeNotification, .NSSystemTimeZoneDidChange] {
            NotificationCenter.default.addObserver(forName: name, object: nil, queue: nil) { _ in AppEventsKt.onTimeChanged() }
        }
        return true
    }

    /// A reminder that fires while the app is open is shown as a banner too, as on Android.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// A reminder tap: `userInfo["itemId"]` is set by `IosNotificationScheduler` (core/platform, ITEM_ID_KEY).
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if let id = response.notification.request.content.userInfo["itemId"] as? String { openReader(id) }
    }
}
