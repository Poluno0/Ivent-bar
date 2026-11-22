# Building on GitHub (no local Gradle setup needed)


1. Create a GitHub repository (suggested name: `WorldBorderEventForge`). Choose **Public** or **Private**.
2. On the repository page, click **Add file → Upload files**.
3. Extract this project ZIP on your PC, then upload the *contents inside* `WorldBorderEventForge` (including the `.github` folder, `build.gradle`, `settings.gradle`, and `src`) to the repository root. Do not upload the enclosing folder as a nested folder.
4. Commit the upload to the `main` branch.
5. Open the repository's **Actions** tab. The workflow named **Build Forge mod** should run automatically.
6. When the workflow finishes with a green check, open that run and find **Artifacts** near the bottom. Download `WorldBorderEventForge-<commit>`.
7. Extract the downloaded artifact ZIP. The `.jar` inside is the mod file. Put it in your Minecraft instance's `mods` folder. Make sure the instance uses Forge 1.20.1 and Java 17.

If the Actions tab is disabled, go to the repository's **Settings → Actions → General** and enable actions for the repository.

Note: The workflow only compiles the current prototype. Compilation success is not the same as the mod being gameplay-tested. The current code is not the finished visual/event mod; see the main README for what remains.
