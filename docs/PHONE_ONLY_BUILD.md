# Phone-only build route

You do not need Android Studio on your phone.

Use a cloud build runner such as GitHub Actions:
1. Create a GitHub repository from this project.
2. Upload/push the project files.
3. Open the repository's Actions tab in your phone browser.
4. Run **Build Android APK**.
5. When it finishes, download the `AutoGamePlayer-debug` artifact.
6. Install the APK on your Android device (Android may ask you to allow installation from that source).

The included workflow installs JDK, Android SDK/build tools and Gradle on the cloud runner, so the phone is only used for editing/uploading and downloading the APK.

This is the build route to use when you do not have a PC.
