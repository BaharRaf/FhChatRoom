# FhChatRoom

A real-time chat app for Android, built with **Kotlin**, **Jetpack Compose** and **Firebase**. Users can create group rooms, send direct messages to friends, and exchange text, photo and voice messages, with replies, reactions, editing and live online status.

## Features

**Accounts and profiles**
- Sign up and log in with email and password (Firebase Authentication)
- Profile with first and last name, plus a profile photo taken with the camera, picked from the gallery, or chosen from built-in avatars
- Live online/offline status (Firebase Realtime Database presence with `onDisconnect`)

**Rooms**
- Create public or private group rooms with a name and description
- Join, leave and delete rooms; invite friends to a room
- Member list for every room
- Room list updates in real time, showing the last message and its sender

**Messaging**
- Text, photo (gallery or camera) and voice messages
- Reply to a specific message (quoted preview)
- Emoji reactions
- Edit your own messages (shown as *edited*)
- Delete a message for yourself or for everyone
- Forward a message to another room

**Friends and direct messages**
- Search users and send friend requests; accept, decline or cancel them
- Friends list with online status
- One-to-one direct-message rooms, which can be hidden and restored

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3, Navigation Compose, Coil (image loading) |
| Architecture | MVVM: Compose screens → ViewModels → repositories |
| Backend | Firebase Authentication, Cloud Firestore, Cloud Storage, Realtime Database |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 8.8, version catalog |
| Target | minSdk 24 (Android 7.0), target/compile SDK 35 |

## Architecture

```
Compose screens (screen/)
        │  observe state / call actions
        ▼
ViewModels (viewmodel/)        AuthViewModel, RoomViewModel, MessageViewModel,
        │                      FriendViewModel, ProfileViewModel
        ▼
Repositories (data/)           UserRepository, MessageRepository, FriendRepository
        │
        ▼
Firebase  ── Auth ── Firestore ── Storage ── Realtime Database (presence)
```

Repositories return a `Result` type (`data/Result.kt`) and expose Firestore listeners as Kotlin `Flow`s, so screens update in real time. `Injection.kt` provides the shared Firestore instance.

### Data model

| Store | Path | Content |
|---|---|---|
| Firestore | `users/{email}` | Profile (`User`) |
| Firestore | `rooms/{roomId}` | Room metadata, members, last message, private/direct flags (`Room`) |
| Firestore | `rooms/{roomId}/messages/{messageId}` | Messages incl. type, media URL, reactions, reply reference, edit flag (`Message`) |
| Firestore | `friendRequests/{id}` | Friend requests with status (`FriendRequest`) |
| Firestore | `friendships/{id}` | Accepted friendships between two users |
| Cloud Storage | | Profile photos, photo and voice message files |
| Realtime Database | `status/{encodedEmail}` | Online status, kept current with `onDisconnect` |

## Project structure

```
app/src/main/java/com/example/fhchatroom/
├── MainActivity.kt          navigation host and app entry point
├── Screen.kt                navigation routes
├── Injection.kt             shared Firebase instances
├── data/                    models (User, Room, Message, Friend) and repositories
├── viewmodel/               one ViewModel per feature area
├── screen/                  Compose screens and dialogs (chat, rooms, friends, profile, …)
├── ui/theme/                colours, typography, theme
└── util/OnlineStatusUpdater.kt   presence handling
```

## Getting started

### Requirements
- A recent Android Studio that supports Android Gradle Plugin 8.8, with JDK 11+
- An Android device or emulator running API 24 or higher (developed and tested on a Pixel 8 emulator, API 35)

### Firebase setup
The repository contains the `google-services.json` of the original development project. To run the app against your own backend:

1. Create a project in the [Firebase console](https://console.firebase.google.com) and add an Android app with the package name `com.example.fhchatroom`.
2. Enable **Authentication → Email/Password**, **Cloud Firestore**, **Cloud Storage** and **Realtime Database**.
3. Download your `google-services.json` and replace `app/google-services.json`.

### Build and run
```bash
git clone https://github.com/BaharRaf/FhChatRoom.git
cd FhChatRoom
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```
Or open the folder in Android Studio and press **Run**. The app asks for camera and microphone permission the first time you send a photo or voice message.

## Academic context

FhChatRoom was developed at **FH Campus Wien** (Computer Science and Digital Communications). It is the system under test for a two-part bachelor thesis on exploratory testing:

- **Bachelor thesis 1** designs an exploratory testing framework for the app (risk taxonomy, test charters, heuristics, session-based test management).
- **Bachelor thesis 2**, *"Executing Exploratory Testing on a Kotlin-Firebase Real-Time Chat Application"*, carries out and evaluates that framework in 40 test sessions.

The app build was frozen for the whole test campaign. Defects found during testing are documented in the thesis and were intentionally **not** fixed in this repository.

## Authors

- **Sepideh Riahi** ([@Sepidehri](https://github.com/Sepidehri))
- **Bahareh Rafiee** ([@BaharRaf](https://github.com/BaharRaf))
