{
  description = "GitOps Android — Renovate PR manager dev shell";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { self, nixpkgs }:
    let
      system = "x86_64-linux";
      buildToolsVersion = "35.0.0";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ buildToolsVersion ];
        includeNDK = false;
        includeSystemImages = false;
        useGoogleAPIs = false;
      };
      sdk = "${android.androidsdk}/libexec/android-sdk";
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [
          android.androidsdk
          pkgs.jdk21
        ];

        ANDROID_HOME = sdk;
        ANDROID_SDK_ROOT = sdk;
        JAVA_HOME = "${pkgs.jdk21}/lib/openjdk";

        # NixOS can't run AGP's Maven-downloaded aapt2 (it's dynamically linked against a
        # loader that doesn't exist here). Point AGP at the SDK's patched aapt2 instead.
        # Delivered as a project property via a -D system property so it applies to every
        # `nix develop` invocation (local + CI) without baking a /nix/store path into a
        # tracked file.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildToolsVersion}/aapt2";
      };
    };
}
