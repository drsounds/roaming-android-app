# roaming-android-app
An Android stepwatcher and motion sensing app which can send broadcasts about motion to allow for creating new apps that react on physical activity.
# Inputs
1. Stepwatcher
2. On device identification of body motion through on devicd model analyzes device cameram

# Output
A stream of Steps / motion detected as stream of events ````{
motion_type,
direction,
intensity
}````
which are sent as broadcast intents
````se.roaming.app.event.MOTION````
with data
````{
new: { motion_type,
direction,
intensity},
prev: {
motion_type,
direction,
intensity
}
}````

# Privacy

* Only local
* No GPS coordinate
* No health data
* Open Source

# License

Apache license




