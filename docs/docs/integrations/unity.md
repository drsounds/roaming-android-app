---
sidebar_position: 3
title: Unity
---

# Unity

Unity can't register a broadcast receiver from C# directly. Add a small Java plugin that forwards each event to a GameObject with `UnitySendMessage`.

**`Assets/Plugins/Android/RoamingBridge.java`**

```java
package com.example.roaming;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import com.unity3d.player.UnityPlayer;

public class RoamingBridge {
    private static BroadcastReceiver receiver;

    public static void start(Activity activity, final String gameObject) {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String data = intent.getStringExtra("data");
                if (data != null) UnityPlayer.UnitySendMessage(gameObject, "OnRoamingMotion", data);
            }
        };
        ContextCompat.registerReceiver(activity, receiver,
                new IntentFilter("se.spacify.app.roaming.event.MOTION"),
                ContextCompat.RECEIVER_EXPORTED);
    }

    public static void stop(Activity activity) {
        if (receiver == null) return;
        activity.unregisterReceiver(receiver);
        receiver = null;
    }
}
```

`androidx.core` must be available. Add it under **Project Settings → Player → Publishing Settings → Custom Main Gradle Template** if your project doesn't have it yet.

**`RoamingInput.cs`**

```csharp
using UnityEngine;

[System.Serializable] public class RoamingVector { public float x, y, z; }
[System.Serializable] public class RoamingState { public string motion_type; public string direction; public float intensity; public RoamingVector vector; }
[System.Serializable] public class RoamingEvent { public RoamingState @new; public RoamingState prev; public long timestamp; public string source; }

public class RoamingInput : MonoBehaviour
{
    public RoamingState Current { get; private set; }

    void OnEnable()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
        new AndroidJavaClass("com.example.roaming.RoamingBridge").CallStatic("start", activity, gameObject.name);
#endif
    }

    void OnDisable()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
        new AndroidJavaClass("com.example.roaming.RoamingBridge").CallStatic("stop", activity);
#endif
    }

    // Called by RoamingBridge with the JSON from the "data" extra.
    void OnRoamingMotion(string json)
    {
        Current = JsonUtility.FromJson<RoamingEvent>(json).@new;
    }
}
```

Then read `Current.vector.z` as forward speed, `Current.motion_type == "jumping"` as a jump, and so on.
