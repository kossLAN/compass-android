# The SDK and NDK are read-only in the store, so the Android Gradle Plugin cannot install
# anything it is missing: these have to be exactly the versions the build asks for, which is why
# they come from the version catalog.
{ lib, androidenv }:

let
  inherit ((lib.importTOML ../gradle/libs.versions.toml).versions)
    android-compileSdk
    android-buildTools
    android-ndk
    ;

  composition = androidenv.composeAndroidPackages {
    platformVersions = [ android-compileSdk ];
    buildToolsVersions = [ android-buildTools ];
    includeNDK = true;
    ndkVersions = [ android-ndk ];
    includeCmake = false;
    includeEmulator = false;
    includeSystemImages = false;
    includeSources = false;
  };

  root = "${composition.androidsdk}/libexec/android-sdk";
in
{
  inherit root;

  sdk = composition.androidsdk;
  platformTools = composition.platform-tools;
  ndk = "${root}/ndk/${android-ndk}";

  # The Android Gradle Plugin downloads a generic Linux aapt2 that cannot run on NixOS.
  aapt2 = "${root}/build-tools/${android-buildTools}/aapt2";
}
