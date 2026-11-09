{
  nixpkgs,
  rust-overlay,
  system ? builtins.currentSystem,
}:

import nixpkgs {
  inherit system;
  overlays = [ (import rust-overlay) ];
  config = {
    allowUnfree = true;
    android_sdk.accept_license = true;
  };
}
