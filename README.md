# courtPal
## About
A tennis companion app to encourage users to play more tennis by setting up calendar events, tracking their scores and keeping up with friends

Built with Kotlin and Jetpack Compose, courtPal combines a match-scoring tool with a calendar, match history, friends activity feed and personal statistics. It supports both live point-by-point scoring and manual result entry, so players can record a match in the way that suits them

### Features
#### Match tracking
- Live scoring: Record points, games and sets as a match is played
- Manual results: Add the final score of a match without tracking every point
- Customisable settings: Choose best of 1, 3 or 5 sets; configure games per set, advantage or no-ad scoring, tie-breaks and optional rally tracking
- Serving options: Choose the first server or use a coin toss
- Pause and resume: Save an unfinished match and continue it later
- Match history: Review completed and unfinished matches, including set scores and additional match statistics where recorded
- Photos: Take or attach a photo to a completed match
#### Training sessions
- Record tennis training sessions
- Keep track of training activity
#### Calendar and scheduling
- Create and manage tennis events and planned matches
- Schedule a match with a CourtPal friend or enter a guest player's name
- Handle match invitations and proposed changes through pending, confirmed, declined and cancelled states
- Start a scheduled match from the calendar or home screen
- Optionally connect to Google Calendar to create and manage linked calendar events
#### Friends and activity
- Add friends using their CourtPal usernames
- View friends' profile photos and shared match activity
- See matches involving both players in their match histories
- Keep friends' displayed usernames up to date when an account name changes
#### Profiles and personalisation
- View match statistics, achievements and activity streaks
- Unlock badges for milestones such as matches played, streaks and rally achievements
- Set a profile photo and update your username
- Switch between light and dark themes
- Save preferred scoring settings for future matches
- Create an account and log in with a username and password

## Setup
<strong>Requirements</strong>
1. Android Studio with Android SDK installed
2. An Android device or emulator

<strong>Installation</strong>
1. Clone this repository:
```
git clone https://github.com/maddiegee759/courtPalApp
```
2. Open this folder in Android Studio
3. Allow Gradle to sync and install any requested Android SDK components

<strong>Run the app</strong>
1. Select a physical device or emulator in 'Device Manager'
   To connect an Android device enable USB or wireless debugging
2. Select the device from the toolbar dropdown, then press the <strong>green arrow to run</strong>

## Current limitations
- User accounts, friendships and shared match history currently operate
  within the same local database. Cross-device synchronisation is not
  yet supported
- Google Calendar integration requires internet access and Google
  authorisation
- The app is designed for portrait orientation
