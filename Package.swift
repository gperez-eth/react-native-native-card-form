// swift-tools-version:5.9
import PackageDescription

// Test-only manifest: exists so `swift test` can run CardValidation.swift's
// real logic against the real, compiled Stripe SDK without needing the full
// Xcode project the example app generates via `expo prebuild`. It plays no
// part in how the package is consumed by React Native / CocoaPods — that
// path is untouched (expo-module.config.json + the Podspec CocoaPods
// resolves from it).
let package = Package(
  name: "NativeCardFormValidation",
  platforms: [.iOS(.v15)],
  products: [
    .library(name: "CardValidationCore", targets: ["CardValidationCore"])
  ],
  dependencies: [
    .package(url: "https://github.com/stripe/stripe-ios-spm.git", exact: "24.25.0")
  ],
  targets: [
    .target(
      name: "CardValidationCore",
      dependencies: [.product(name: "StripePayments", package: "stripe-ios-spm")],
      path: "ios",
      exclude: [
        "MackenrowNativeCardModule.swift",
        "MackenrowNativeCardView.swift",
        "CardSessionRegistry.swift",
        "ReactNativeNativeCardForm.podspec"
      ],
      sources: ["CardValidation.swift"]
    ),
    .testTarget(
      name: "CardValidationTests",
      dependencies: ["CardValidationCore"],
      path: "Tests/CardValidationTests"
    )
  ]
)
