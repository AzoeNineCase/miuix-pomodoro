import SwiftUI
import ComposeApp

/// 把 Kotlin/Compose 的 MainViewController 包进 SwiftUI
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.keyboard) // Compose 自行处理键盘避让
        }
    }
}
