# Repository Layout

The root README is the public product overview. Detailed audits, implementation plans and build notes are kept under `docs/` so the GitHub landing page is not overloaded.

```
.github/workflows/       CI build workflow
app/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    java/com/paperplan/app/
      MainActivity.kt
      core/ui/theme/      Compose theme tokens
      feature/home/       Home screen and tool catalog
      feature/scanner/    Scanner implementation (planned)
      feature/ocr/        OCR implementation (planned)
      feature/translation/ Online translation (planned)
      feature/designer/   PDF designer (planned)
    res/
      drawable/           Vector app icon artwork
      mipmap-anydpi-v26/  Adaptive launcher icon
      values/             App labels, colors and theme
docs/                    Product plans and source audits
```

Feature folders are added as implementation begins; empty placeholder files are avoided.
