{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
    }:
    let
      forEachSystem =
        fn:
        nixpkgs.lib.genAttrs [ "x86_64-linux" ] (
          system: fn (import ./nix/nixpkgs.nix { inherit nixpkgs rust-overlay system; })
        );
    in
    {
      devShells = forEachSystem (pkgs: {
        default = import ./shell.nix { inherit pkgs; };
      });

      formatter = forEachSystem (pkgs: pkgs.nixfmt-tree);
    };
}
