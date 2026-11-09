{
  pkgs ? import ./nix/nixpkgs.nix (builtins.getFlake (toString ./.)).inputs,
}:
let
  android = pkgs.callPackage ./nix/android-sdk.nix { };

  rust = pkgs.rust-bin.stable.latest.minimal.override {
    extensions = [
      "clippy"
      "rustfmt"
    ];
    targets = [
      "aarch64-linux-android"
      "x86_64-linux-android"
    ];
  };

  jdk = pkgs.jdk21;
  gradle = pkgs.gradle_9.override { java = jdk; };
in
pkgs.mkShell {
  nativeBuildInputs = [
    rust
    pkgs.cargo-ndk
    gradle
    jdk
    android.sdk
  ];

  packages = [
    android.platformTools
    pkgs.apksigner
    pkgs.ktlint
    pkgs.kotlin-language-server
  ];

  JAVA_HOME = jdk.home;
  ANDROID_HOME = android.root;
  ANDROID_SDK_ROOT = android.root;
  ANDROID_NDK_HOME = android.ndk;
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${android.aapt2}";
}
