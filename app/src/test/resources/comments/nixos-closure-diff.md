<!-- nixos-ci-closure-diff -->
### Closure diff vs `14b7df47`

<details><summary><b>desktop</b>: Closure size: 3470 -> 3469 (309 paths added, 310 paths removed, delta -1).</summary>

```
<<< /nix/store/wyjlzn23mwhrir2r2lsx4cmj1z3ci2pa-nixos-system-desktop-26.05.20261105.c56a87d
>>> /nix/store/2aw8skmfgpv8jssalf4bfgp6l59zlf99-all-hosts/desktop
Version changes:
[C.]  #01  brotli                     1.2.0-lib, 1.2.1, 1.2.1-dev, 1.2.1-lib x2 -> 1.2.1, 1.2.1-dev, 1.2.1-lib x3
[C.]  #02  bubblewrap                 0.12.0 x2 -> 0.12.0, 0.13.1
[C.]  #03  cairo                      1.18.4 x3, 1.18.4-dev -> 1.18.4 x2, 1.18.4-dev, 1.18.6
[C*]  #04  curl                       8.22.0, 8.23.0 x5, 8.23.0-bin x2, 8.23.0-man x2 -> 8.23.0 x6, 8.23.0-bin x2, 8.23.0-man x2
[C*]  #05  dconf                      0.49.0, 0.49.0-lib x3 -> 0.49.0, 0.49.0-lib x2, 51.0-lib
[C.]  #06  fribidi                    1.0.17 x3, 1.0.17-dev -> 1.0.17 x2, 1.0.17-dev, 1.0.18
[C*]  #07  glib                       2.88.3 x3, 2.88.3-bin, 2.88.3-dev -> 2.88.3 x2, 2.88.3-bin, 2.88.3-dev, 2.90.0
[C.]  #08  gsettings-desktop-schemas  50.1 x2 -> 50.1, 51.0
[C.]  #09  gst-plugins-bad            1.26.11, 1.28.7 -> 1.26.11, 1.28.8
[C.]  #10  gst-plugins-base           1.26.11 x2, 1.26.11-dev, 1.28.7 -> 1.26.11 x2, 1.26.11-dev, 1.28.8
[C.]  #11  gstreamer                  1.26.11 x2, 1.26.11-bin, 1.26.11-dev, 1.28.7 -> 1.26.11 x2, 1.26.11-bin, 1.26.11-dev, 1.28.8
[C.]  #12  gtk4                       4.22.4, 4.22.5, 4.22.5-dev -> 4.22.5, 4.22.5-dev, 4.24.0
[C.]  #13  hunspell                   1.7.2, 1.7.3 -> 1.7.2, 1.7.5
[C.]  #14  json-c                     0.18 x3 -> 0.18 x2, 0.19
[C.]  #15  libajantv2                 17.5.0 x2 -> 17.5.0, 18.0.0
[C.]  #16  libaom                     3.15.1 x3 -> 3.15.1 x2, 3.15.2
[C.]  #17  libarchive                 3.8.9, 3.8.9-lib x3 -> 3.8.9, 3.8.9-lib x2, 3.8.10-lib
[C.]  #18  libass                     0.17.4, 0.17.5 x2 -> 0.17.5 x3
[C.]  #19  libde265                   1.1.2, 1.1.3 -> 1.1.3 x2
[C.]  #20  libmad                     0.15.1b x3 -> 0.15.1b x2, 0.16.5
[C.]  #21  libpsl                     0.21.5 x3 -> 0.21.5 x2, 0.23.3
[C.]  #22  librsvg                    2.62.3, 2.62.4 -> 2.62.4, 2.63.0
[C.]  #23  libsecret                  0.21.7, 0.21.8.2 -> 0.21.8.2 x2
[C.]  #24  openexr                    3.4.15, 3.5.1 -> 3.4.15, 3.5.2
[C.]  #25  pango                      1.57.1 x3, 1.57.1-bin, 1.57.1-dev -> 1.57.1 x2, 1.57.1-bin, 1.57.1-dev, 1.58.2
[C*]  #26  pipewire                   1.6.6 x2, 1.6.6-dev, 1.6.6-doc, 1.6.6-man, 1.6.9 -> 1.6.6 x2, 1.6.6-dev, 1.6.6-doc, 1.6.6-man, 1.6.11
[C*]  #27  python3                    3.13.17 x2, 3.13.17-env x6, 3.14.8 -> 3.13.17 x2, 3.13.17-env x6, 3.14.10
[C.]  #28  sqlite                     3.53.3 x3, 3.53.3-bin, 3.53.3-dev -> 3.53.3 x2, 3.53.3-bin, 3.53.3-dev, 3.54.0
[C*]  #29  tinysparql                 3.11.1 x2 -> 3.11.1, 3.12.0
[C.]  #30  tzdata                     2026e x3 -> 2026e x2, 2026f
[C.]  #31  unbound                    1.26.1-lib, 1.26.2-lib x2 -> 1.26.2-lib x3
[C.]  #32  webkitgtk                  2.54.1+abi=4.1, 2.54.1+abi=6.0 x2 -> 2.54.1+abi=4.1, 2.54.1+abi=6.0, 2.54.2+abi=6.0
[C.]  #33  xkeyboard-config           2.47, 2.48 -> 2.47, 2.49
[C.]  #34  zlib                       1.3.2 x3, 1.3.2-dev, 1.3.2-static x3 -> 1.3.2 x3, 1.3.2-dev, 1.3.2-static x2
[C.]  #35  zvbi                       0.2.45 x3 -> 0.2.45 x2, 0.2.46
Closure size: 3470 -> 3469 (309 paths added, 310 paths removed, delta -1, disk usage +4.5GiB).
```

</details>
<details><summary><b>dev-01</b>: Closure size: 1390 -> 1390 (20 paths added, 20 paths removed, delta +0).</summary>

```
<<< /nix/store/f977i0ilmgszn9s20mxq9m0ifag7wail-nixos-system-dev-01-26.05.20261105.c56a87d
>>> /nix/store/2aw8skmfgpv8jssalf4bfgp6l59zlf99-all-hosts/dev-01
Version changes:
[C.]  #1  tzdata  2026e x2 -> 2026e, 2026f
Closure size: 1390 -> 1390 (20 paths added, 20 paths removed, delta +0, disk usage +32.2GiB).
```

</details>
<details><summary><b>laptop</b>: Closure size: 3508 -> 3506 (356 paths added, 358 paths removed, delta -2).</summary>

```
<<< /nix/store/5dzkwxksgxpzh22xva0j70nf8y4j4vn2-nixos-system-laptop-26.05.20261105.c56a87d
>>> /nix/store/2aw8skmfgpv8jssalf4bfgp6l59zlf99-all-hosts/laptop
Version changes:
[D.]  #01  ansi-regex                 6.6.0.tgz -> 6.5.0.tgz
[C.]  #02  brotli                     1.2.0-lib, 1.2.1, 1.2.1-dev, 1.2.1-lib x2 -> 1.2.1, 1.2.1-dev, 1.2.1-lib x3
[C.]  #03  bubblewrap                 0.12.0 x2 -> 0.12.0, 0.13.1
[C.]  #04  cairo                      1.18.4 x3, 1.18.4-dev -> 1.18.4 x2, 1.18.4-dev, 1.18.6
[C*]  #05  curl                       8.22.0, 8.23.0 x5, 8.23.0-bin x2, 8.23.0-man x2 -> 8.23.0 x6, 8.23.0-bin x2, 8.23.0-man x2
[C*]  #06  dconf                      0.49.0, 0.49.0-lib x3 -> 0.49.0, 0.49.0-lib x2, 51.0-lib
[C.]  #07  fribidi                    1.0.17 x3, 1.0.17-dev -> 1.0.17 x2, 1.0.17-dev, 1.0.18
[C*]  #08  glib                       2.88.3 x3, 2.88.3-bin, 2.88.3-dev -> 2.88.3 x2, 2.88.3-bin, 2.88.3-dev, 2.90.0
[C.]  #09  gsettings-desktop-schemas  50.1 x2 -> 50.1, 51.0
[C.]  #10  gst-plugins-bad            1.26.11, 1.28.7 -> 1.26.11, 1.28.8
[C.]  #11  gst-plugins-base           1.26.11 x2, 1.26.11-dev, 1.28.7 -> 1.26.11 x2, 1.26.11-dev, 1.28.8
[C.]  #12  gstreamer                  1.26.11 x2, 1.26.11-bin, 1.26.11-dev, 1.28.7 -> 1.26.11 x2, 1.26.11-bin, 1.26.11-dev, 1.28.8
[C.]  #13  gtk4                       4.22.4, 4.22.5, 4.22.5-dev -> 4.22.5, 4.22.5-dev, 4.24.0
[C.]  #14  hunspell                   1.7.2, 1.7.3 -> 1.7.2, 1.7.5
[C.]  #15  json-c                     0.18 x3 -> 0.18 x2, 0.19
[C.]  #16  libajantv2                 17.5.0 x2 -> 17.5.0, 18.0.0
[C.]  #17  libaom                     3.15.1 x3 -> 3.15.1 x2, 3.15.2
[C.]  #18  libarchive                 3.8.9, 3.8.9-lib x3 -> 3.8.9, 3.8.9-lib x2, 3.8.10-lib
[C.]  #19  libass                     0.17.4, 0.17.5 x2 -> 0.17.5 x3
[C.]  #20  libde265                   1.1.2, 1.1.3 -> 1.1.3 x2
[C.]  #21  libmad                     0.15.1b x3 -> 0.15.1b x2, 0.16.5
[C.]  #22  libpsl                     0.21.5 x3 -> 0.21.5 x2, 0.23.3
[C.]  #23  librsvg                    2.62.3, 2.62.4 -> 2.62.4, 2.63.0
[C.]  #24  libsecret                  0.21.7, 0.21.8.2 -> 0.21.8.2 x2
[C.]  #25  linux-headers              6.18.7, 7.2 -> 6.18.7, 7.3
[C.]  #26  openexr                    3.4.15, 3.5.1 -> 3.4.15, 3.5.2
[C.]  #27  pango                      1.57.1 x3, 1.57.1-bin, 1.57.1-dev -> 1.57.1 x2, 1.57.1-bin, 1.57.1-dev, 1.58.2
[C*]  #28  pipewire                   1.6.6 x2, 1.6.6-dev, 1.6.6-doc, 1.6.6-man, 1.6.9 -> 1.6.6 x2, 1.6.6-dev, 1.6.6-doc, 1.6.6-man, 1.6.11
[C*]  #29  python3                    3.13.17 x2, 3.13.17-env x6, 3.14.8 -> 3.13.17 x2, 3.13.17-env x6, 3.14.10
[C.]  #30  semver                     7.8.5.tgz, 7.8.6.tgz -> 7.8.5.tgz
[C.]  #31  sqlite                     3.53.3 x3, 3.53.3-bin, 3.53.3-dev -> 3.53.3 x2, 3.53.3-bin, 3.53.3-dev, 3.54.0
[C*]  #32  tinysparql                 3.11.1 x2 -> 3.11.1, 3.12.0
[C.]  #33  tzdata                     2026e x3 -> 2026e x2, 2026f
[C.]  #34  unbound                    1.26.1-lib, 1.26.2-lib x2 -> 1.26.2-lib x3
[C.]  #35  webkitgtk                  2.54.1+abi=4.1, 2.54.1+abi=6.0 x2 -> 2.54.1+abi=4.1, 2.54.1+abi=6.0, 2.54.2+abi=6.0
[C.]  #36  ws                         8.21.0.tgz, 8.22.2.tgz -> 8.21.0.tgz, 8.22.1.tgz
[C.]  #37  xkeyboard-config           2.47, 2.48 -> 2.47, 2.49
[C.]  #38  zlib                       1.3.2 x3, 1.3.2-dev, 1.3.2-static x3 -> 1.3.2 x3, 1.3.2-dev, 1.3.2-static x2
[C.]  #39  zvbi                       0.2.45 x3 -> 0.2.45 x2, 0.2.46
Closure size: 3508 -> 3506 (356 paths added, 358 paths removed, delta -2, disk usage +6.4GiB).
```

</details>
