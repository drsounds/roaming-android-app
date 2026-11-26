# roaming-android-app
An Android stepwatcher and motion sensing app which can send broadcasts about motion to allow for creating new apps that react on physical activity.
# Inputs
1. Stepwatcher
2. On device identification of body motion through on devicd model analyzes device cameram

# Output
A stream of Steps / motion detected as stream of events 

````json
{
motion_type,
direction,
intensity
}
````

which are sent as broadcast intents

````se.spacify.app.roaming.event.MOTION````

with data

````json
{
new: { motion_type,
direction,
intensity},
prev: {
motion_type,
direction,
intensity
}
}
````

# Privacy

* Only local
* No GPS coordinate
* No health data
* Open Source
* Use a foregroundservice with ongoing notificatoon so it only broadcasts when a pendingintent is live so the user always know it is tracking

# License

Apache license

# CI 
It should have a CI GitHub Actions workflow for automatically building this app


